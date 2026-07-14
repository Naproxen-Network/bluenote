package com.littlebluenote.recommend.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * In-memory holder for the precomputed Multi-Layer Hypergraph Recommender (MLHR) model.
 *
 * The three signals per user come from the two real hypergraph layers:
 *   struct : intra-layer co-sponsorship structure on the 小蓝书 (bills) layer
 *   sem    : profile-text (TF-IDF) semantic similarity on the 小蓝书 layer
 *   cross  : neighbours discovered through the committee layer, mapped back to 小蓝书
 *            (the real-data analogue of the TikTok / Telegram influencing layers)
 *
 * The fusion weights (alpha/beta/gamma, semanticRatio, modulation) were learned offline
 * and are shipped in sa_weights -> recommend_model.json.
 */
@Component
public class MlhrModel {

    public static class Signal {
        public final int id;
        public final double score;
        public Signal(int id, double score) { this.id = id; this.score = score; }
    }

    public double alpha, beta, gamma, semanticRatio, modulationLb, modulationSc;

    // userId -> component neighbour lists
    private final Map<Integer, List<Signal>> struct = new HashMap<>();
    private final Map<Integer, List<Signal>> sem = new HashMap<>();
    private final Map<Integer, List<Signal>> cross = new HashMap<>();
    private final Map<Integer, Integer> crossMapping = new HashMap<>();
    private final Set<Integer> allUsers = new HashSet<>();

    @PostConstruct
    public void load() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode root;
        try (var in = new ClassPathResource("recommend_model.json").getInputStream()) {
            root = om.readTree(in);
        }
        JsonNode w = root.get("weights");
        alpha = w.get("alpha_struct").asDouble();
        beta = w.get("beta_semantic").asDouble();
        gamma = w.get("gamma_cross").asDouble();
        semanticRatio = w.get("semanticRatio").asDouble();
        modulationLb = w.get("modulationLb").asDouble();
        modulationSc = w.get("modulationSc").asDouble();

        JsonNode users = root.get("users");
        Iterator<Map.Entry<String, JsonNode>> it = users.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            int uid = Integer.parseInt(e.getKey());
            allUsers.add(uid);
            struct.put(uid, parse(e.getValue().get("struct")));
            sem.put(uid, parse(e.getValue().get("sem")));
            cross.put(uid, parse(e.getValue().get("cross")));
        }
        JsonNode cm = root.get("crossLayerMapping");
        cm.fields().forEachRemaining(en -> crossMapping.put(
                Integer.parseInt(en.getKey()), en.getValue().asInt()));
    }

    private List<Signal> parse(JsonNode arr) {
        List<Signal> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonNode n : arr) out.add(new Signal(n.get(0).asInt(), n.get(1).asDouble()));
        return out;
    }

    public List<Signal> struct(int u) { return struct.getOrDefault(u, List.of()); }
    public List<Signal> sem(int u) { return sem.getOrDefault(u, List.of()); }
    public List<Signal> cross(int u) { return cross.getOrDefault(u, List.of()); }
    public boolean hasUser(int u) { return allUsers.contains(u); }
    public Set<Integer> users() { return allUsers; }
    public Integer committeeOf(int u) { return crossMapping.get(u); }
}
