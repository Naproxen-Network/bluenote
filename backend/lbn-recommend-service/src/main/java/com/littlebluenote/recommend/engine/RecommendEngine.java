package com.littlebluenote.recommend.engine;

import com.littlebluenote.common.Constants;
import com.littlebluenote.common.dto.PostDTO;
import com.littlebluenote.common.dto.UserDTO;
import com.littlebluenote.recommend.client.PostClient;
import com.littlebluenote.recommend.client.UserClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runtime scorer for the Multi-Layer Hypergraph Recommender.
 *
 * Feed score for user u and a post p authored by a:
 *   affinity(u,a) = alpha*struct_B(u,a) + beta*sem_B(u,a) + gamma*cross_C(u,a)
 *                    + modulationSc * dynamicBoost(a)            (committee-driven, real-time)
 *   score(u,p)    = affinity(u,a)
 *                    + interestBonus(u, field(p))
 *                    + engagementBonus(p)                        (likes/favs/comments)
 *                    + freshnessBonus(p)
 *
 * dynamicBoost(a) is a decaying value in Redis that the committee-change consumer bumps
 * whenever the committee (other) layer shifts, so recommendations update live.
 */
@Service
public class RecommendEngine {

    private final MlhrModel model;
    private final PostClient postClient;
    private final UserClient userClient;
    private final StringRedisTemplate redis;

    private volatile List<PostDTO> postSnapshot = List.of();
    private volatile long snapshotAt = 0;
    private final Map<Integer, UserDTO> userCache = new HashMap<>();

    public RecommendEngine(MlhrModel model, PostClient postClient,
                           UserClient userClient, StringRedisTemplate redis) {
        this.model = model;
        this.postClient = postClient;
        this.userClient = userClient;
        this.redis = redis;
    }

    // ---------- data snapshots ----------
    private synchronized List<PostDTO> posts() {
        long now = System.currentTimeMillis();
        if (now - snapshotAt > 30_000 || postSnapshot.isEmpty()) {
            try {
                var r = postClient.all();
                if (r != null && r.getData() != null) {
                    postSnapshot = r.getData();
                    snapshotAt = now;
                }
            } catch (Exception ignore) { }
        }
        return postSnapshot;
    }

    public void invalidateSnapshot() { snapshotAt = 0; }

    private UserDTO user(int id) {
        UserDTO u = userCache.get(id);
        if (u != null) return u;
        hydrate(List.of(id));
        return userCache.get(id);
    }

    private void hydrate(Collection<Integer> ids) {
        List<Integer> missing = ids.stream().filter(i -> !userCache.containsKey(i)).distinct().toList();
        if (missing.isEmpty()) return;
        try {
            String csv = missing.stream().map(String::valueOf).collect(Collectors.joining(","));
            var r = userClient.batch(csv);
            if (r != null && r.getData() != null) {
                for (UserDTO d : r.getData()) userCache.put(d.getId().intValue(), d);
            }
        } catch (Exception ignore) { }
    }

    // ---------- core affinity ----------
    /** authorId -> {score, dominant-component label} */
    public Map<Integer, double[]> affinity(int u) {
        // index 0 = fused score, 1 = struct, 2 = sem, 3 = cross (for explanation)
        Map<Integer, double[]> m = new HashMap<>();
        for (MlhrModel.Signal s : model.struct(u))
            m.computeIfAbsent(s.id, k -> new double[4])[1] += s.score;
        for (MlhrModel.Signal s : model.sem(u))
            m.computeIfAbsent(s.id, k -> new double[4])[2] += s.score;
        for (MlhrModel.Signal s : model.cross(u))
            m.computeIfAbsent(s.id, k -> new double[4])[3] += s.score;

        Map<Object, Object> boosts = redis.opsForHash().entries(Constants.REDIS_CROSS_BOOST);

        for (var e : m.entrySet()) {
            double[] v = e.getValue();
            double fused = model.alpha * v[1] + model.beta * v[2] + model.gamma * v[3];
            // real-time committee-layer modulation
            Object b = boosts.get(String.valueOf(e.getKey()));
            if (b != null) {
                double delta = safeDouble(b);
                boolean crossNeighbor = v[3] > 0;
                fused += model.modulationSc * delta * (crossNeighbor ? (1 + model.modulationLb) : 1);
            }
            v[0] = fused;
        }
        return m;
    }

    private double safeDouble(Object o) {
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    // ---------- discover feed ----------
    public Map<String, Object> feed(int u, int page, int size) {
        String version = redis.opsForValue().get("lbn:rec:version");
        String cacheKey = Constants.REDIS_REC_PREFIX + u + ":" + version + ":" + page + ":" + size;
        // (cache stores ordered post-id csv; we still re-hydrate for fresh counts)
        Map<Integer, double[]> aff = affinity(u);
        UDInterests interests = interests(u);

        List<PostDTO> all = posts();
        List<ScoredPost> scored = new ArrayList<>();
        for (PostDTO p : all) {
            int author = p.getAuthorId().intValue();
            double[] a = aff.get(author);
            double base = a == null ? 0.0 : a[0];
            double interestBonus = interests.fields.contains(p.getField()) ? 0.18 : 0.0;
            double engagement = engagement(p);
            double freshness = Math.min(0.12, p.getId() / (double) (maxPostId(all) + 1) * 0.12);
            double ownPenalty = author == u ? -0.5 : 0.0;
            double score = base + interestBonus + 0.06 * engagement + freshness + ownPenalty;
            scored.add(new ScoredPost(p, score, reason(a, interestBonus > 0)));
        }
        scored.sort((x, y) -> Double.compare(y.score, x.score));

        int from = (page - 1) * size;
        int to = Math.min(from + size, scored.size());
        List<ScoredPost> pageItems = from >= scored.size() ? List.of() : scored.subList(from, to);

        hydrate(pageItems.stream().map(sp -> sp.post.getAuthorId().intValue()).toList());
        List<Map<String, Object>> records = new ArrayList<>();
        for (ScoredPost sp : pageItems) records.add(present(sp));

        redis.opsForValue().set(cacheKey,
                pageItems.stream().map(sp -> String.valueOf(sp.post.getId())).collect(Collectors.joining(",")));
        Map<String, Object> out = new HashMap<>();
        out.put("total", scored.size());
        out.put("records", records);
        out.put("weights", Map.of("alpha", model.alpha, "beta", model.beta,
                "gamma", model.gamma, "modulationSc", model.modulationSc));
        return out;
    }

    // ---------- search (also algorithm-driven) ----------
    public Map<String, Object> search(int u, String query, int page, int size) {
        String q = query == null ? "" : query.toLowerCase().trim();
        List<String> tokens = Arrays.stream(q.split("\\W+")).filter(t -> t.length() >= 2).toList();
        Map<Integer, double[]> aff = affinity(u);
        List<PostDTO> all = posts();
        hydrate(all.stream().map(p -> p.getAuthorId().intValue()).distinct().toList());

        List<ScoredPost> scored = new ArrayList<>();
        for (PostDTO p : all) {
            UserDTO au = userCache.get(p.getAuthorId().intValue());
            String hay = (p.getContent() + " " + p.getField() + " " + p.getTags() + " "
                    + (au != null ? au.getDisplayName() + " " + au.getPosition() + " "
                    + String.join(" ", au.getInterests() == null ? List.of() : au.getInterests()) : "")).toLowerCase();
            double text = 0;
            for (String t : tokens) if (hay.contains(t)) text += 1.0;
            if (tokens.isEmpty()) text = 0.2; // browse-all
            if (text <= 0) continue;
            double[] a = aff.get(p.getAuthorId().intValue());
            double crossInfluence = a == null ? 0 : a[0];
            double score = text * (1.0 + 0.6 * crossInfluence) + 0.05 * engagement(p);
            scored.add(new ScoredPost(p, score, a != null && a[3] > 0 ? "跨层网络相关" : "语义相关"));
        }
        scored.sort((x, y) -> Double.compare(y.score, x.score));
        int from = (page - 1) * size;
        int to = Math.min(from + size, scored.size());
        List<ScoredPost> items = from >= scored.size() ? List.of() : scored.subList(from, to);
        List<Map<String, Object>> records = new ArrayList<>();
        for (ScoredPost sp : items) records.add(present(sp));
        Map<String, Object> out = new HashMap<>();
        out.put("total", scored.size());
        out.put("records", records);
        return out;
    }

    // ---------- "people you may connect with" (pure multi-layer) ----------
    public List<Map<String, Object>> suggestedUsers(int u, int n) {
        Map<Integer, double[]> aff = affinity(u);
        List<Map.Entry<Integer, double[]>> top = aff.entrySet().stream()
                .filter(e -> e.getKey() != u)
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(n).toList();
        hydrate(top.stream().map(Map.Entry::getKey).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : top) {
            UserDTO d = userCache.get(e.getKey());
            if (d == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("user", d);
            m.put("score", round(e.getValue()[0]));
            m.put("reason", reason(e.getValue(), false));
            out.add(m);
        }
        return out;
    }

    // ---------- helpers ----------
    private record UDInterests(Set<String> fields) {}

    private UDInterests interests(int u) {
        UserDTO d = user(u);
        Set<String> f = new HashSet<>();
        if (d != null && d.getInterests() != null) f.addAll(d.getInterests());
        return new UDInterests(f);
    }

    private double engagement(PostDTO p) {
        int like = nz(p.getLikeCount()), fav = nz(p.getFavoriteCount()), com = nz(p.getCommentCount());
        return Math.log1p(like + 2.0 * fav + com);
    }

    private int nz(Integer i) { return i == null ? 0 : i; }

    private long maxPostId(List<PostDTO> all) {
        long m = 1;
        for (PostDTO p : all) m = Math.max(m, p.getId());
        return m;
    }

    private String reason(double[] a, boolean interestMatched) {
        if (a == null) return interestMatched ? "兴趣领域匹配" : "热门推荐";
        double s = a[1] * model.alpha, se = a[2] * model.beta, c = a[3] * model.gamma;
        if (c >= s && c >= se && c > 0) return "跨层网络影响（委员会层）";
        if (s >= se && s > 0) return "合作网络相似（法案层）";
        if (se > 0) return "研究兴趣相近";
        return interestMatched ? "兴趣领域匹配" : "热门推荐";
    }

    private Map<String, Object> present(ScoredPost sp) {
        PostDTO p = sp.post;
        UserDTO au = userCache.get(p.getAuthorId().intValue());
        if (au != null) {
            p.setAuthorName(au.getDisplayName());
            p.setAuthorAvatar(au.getAvatar());
            p.setAuthorPosition(au.getPosition());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("post", p);
        m.put("score", round(sp.score));
        m.put("reason", sp.reason);
        return m;
    }

    private double round(double d) { return Math.round(d * 1000.0) / 1000.0; }

    private static class ScoredPost {
        final PostDTO post; final double score; final String reason;
        ScoredPost(PostDTO post, double score, String reason) {
            this.post = post; this.score = score; this.reason = reason;
        }
    }
}
