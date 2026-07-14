package com.littlebluenote.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.JwtUtil;
import com.littlebluenote.common.dto.UserDTO;
import com.littlebluenote.user.entity.Admin;
import com.littlebluenote.user.entity.Follow;
import com.littlebluenote.user.entity.User;
import com.littlebluenote.user.mapper.AdminMapper;
import com.littlebluenote.user.mapper.FollowMapper;
import com.littlebluenote.user.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class UserBizService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final FollowMapper followMapper;
    private final StringRedisTemplate redis;

    public UserBizService(UserMapper userMapper, AdminMapper adminMapper,
                          FollowMapper followMapper, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
        this.followMapper = followMapper;
        this.redis = redis;
    }

    // ---- Auth ----
    public Map<String, Object> login(String username, String password) {
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (u == null || !u.getPassword().equals(password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = JwtUtil.issue(u.getId(), Constants.ROLE_USER);
        // mark online in Redis (7-day session mirror)
        redis.opsForSet().add(Constants.REDIS_ONLINE, String.valueOf(u.getId()));
        redis.opsForValue().set("lbn:token:" + u.getId(), token, Constants.JWT_TTL_MS, TimeUnit.MILLISECONDS);
        Map<String, Object> m = new HashMap<>();
        m.put("token", token);
        m.put("role", Constants.ROLE_USER);
        m.put("user", toDTO(u));
        return m;
    }

    public Map<String, Object> adminLogin(String username, String password) {
        Admin a = adminMapper.selectOne(new QueryWrapper<Admin>().eq("username", username));
        if (a == null || !a.getPassword().equals(password)) {
            throw new IllegalArgumentException("Invalid admin credentials");
        }
        String token = JwtUtil.issue(a.getId(), Constants.ROLE_ADMIN);
        Map<String, Object> m = new HashMap<>();
        m.put("token", token);
        m.put("role", Constants.ROLE_ADMIN);
        m.put("name", a.getName());
        return m;
    }

    public UserDTO register(User u) {
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", u.getUsername())) != null) {
            throw new IllegalArgumentException("Username already exists");
        }
        // synthetic id above the seeded range
        Long maxId = userMapper.selectObjs(new QueryWrapper<User>().select("IFNULL(MAX(id),0) as id"))
                .stream().findFirst().map(o -> Long.valueOf(String.valueOf(o))).orElse(0L);
        u.setId(maxId + 1);
        if (u.getAvatar() == null || u.getAvatar().isEmpty()) {
            u.setAvatar("/avatars/" + u.getId() + ".svg");
        }
        userMapper.insert(u);
        return toDTO(u);
    }

    // ---- Profile ----
    public UserDTO getUser(Long id) {
        String key = "lbn:user:" + id;
        User u = userMapper.selectById(id);
        if (u == null) return null;
        return toDTO(u);
    }

    public List<UserDTO> getUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<User> list = userMapper.selectBatchIds(ids);
        List<UserDTO> out = new ArrayList<>();
        for (User u : list) out.add(toDTO(u));
        return out;
    }

    public IPage<User> pageUsers(int page, int size, String keyword) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like("display_name", keyword).or().like("position", keyword)
              .or().like("bio", keyword).or().like("interests", keyword);
        }
        qw.orderByAsc("id");
        return userMapper.selectPage(new Page<>(page, size), qw);
    }

    public void updateProfile(Long id, User patch) {
        patch.setId(id);
        patch.setPassword(null); // do not allow blind password change here
        patch.setUsername(null);
        userMapper.updateById(patch);
    }

    // ---- Follow ----
    public boolean follow(Long follower, Long followee) {
        if (follower.equals(followee)) return false;
        if (followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("follower_id", follower).eq("followee_id", followee)) > 0) {
            return true;
        }
        Follow f = new Follow();
        f.setFollowerId(follower);
        f.setFolloweeId(followee);
        followMapper.insert(f);
        return true;
    }

    public void unfollow(Long follower, Long followee) {
        followMapper.delete(new QueryWrapper<Follow>()
                .eq("follower_id", follower).eq("followee_id", followee));
    }

    public List<Long> following(Long follower) {
        return followMapper.selectList(new QueryWrapper<Follow>().eq("follower_id", follower))
                .stream().map(Follow::getFolloweeId).toList();
    }

    public boolean isFollowing(Long follower, Long followee) {
        return followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("follower_id", follower).eq("followee_id", followee)) > 0;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("totalUsers", userMapper.selectCount(null));
        m.put("onlineUsers", redis.opsForSet().size(Constants.REDIS_ONLINE));
        m.put("totalFollows", followMapper.selectCount(null));
        return m;
    }

    public UserDTO toDTO(User u) {
        UserDTO d = new UserDTO();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setDisplayName(u.getDisplayName());
        d.setParty(u.getParty());
        d.setLeaning(u.getLeaning());
        d.setPosition(u.getPosition());
        d.setAlmaMater(u.getAlmaMater());
        d.setState(u.getState());
        d.setBio(u.getBio());
        d.setCommitteeId(u.getCommitteeId());
        d.setAvatar(u.getAvatar());
        if (u.getInterests() != null && !u.getInterests().isBlank()) {
            d.setInterests(Arrays.asList(u.getInterests().split("\\|")));
        } else {
            d.setInterests(List.of());
        }
        return d;
    }
}
