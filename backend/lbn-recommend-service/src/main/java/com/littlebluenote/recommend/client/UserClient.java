package com.littlebluenote.recommend.client;

import com.littlebluenote.common.Result;
import com.littlebluenote.common.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** OpenFeign client -> user-service (resolved through Nacos + load balancer). */
@FeignClient(name = "lbn-user-service")
public interface UserClient {
    @GetMapping("/api/user/batch")
    Result<List<UserDTO>> batch(@RequestParam("ids") String ids);
}
