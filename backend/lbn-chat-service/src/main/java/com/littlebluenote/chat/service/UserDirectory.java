package com.littlebluenote.chat.service;

import com.littlebluenote.chat.client.UserClient;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.common.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserDirectory {
    private final UserClient client;

    public UserDirectory(UserClient client) {
        this.client = client;
    }

    public UserDTO require(long userId) {
        UserDTO user = batch(List.of(userId)).get(userId);
        if (user == null) throw BusinessException.notFound("User not found");
        return user;
    }

    public UserDTO lookup(String identity) {
        if (identity == null || identity.isBlank() || identity.strip().length() > 64) {
            throw BusinessException.badRequest("A valid user id or username is required");
        }
        try {
            var result = client.lookup(identity.strip());
            if (result == null || result.getData() == null) {
                throw BusinessException.notFound("User not found");
            }
            return result.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(503, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "User service is temporarily unavailable");
        }
    }

    public Map<Long, UserDTO> batch(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        try {
            String csv = distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
            var result = client.batch(csv);
            Map<Long, UserDTO> out = new LinkedHashMap<>();
            if (result != null && result.getData() != null) {
                for (UserDTO user : result.getData()) out.put(user.getId(), user);
            }
            return out;
        } catch (Exception e) {
            throw new BusinessException(503, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "User service is temporarily unavailable");
        }
    }
}
