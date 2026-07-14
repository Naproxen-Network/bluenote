package com.littlebluenote.recommend.client;

import com.littlebluenote.common.Result;
import com.littlebluenote.common.dto.PostDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** OpenFeign client -> post-service (resolved through Nacos + load balancer). */
@FeignClient(name = "lbn-post-service")
public interface PostClient {
    @GetMapping("/api/post/all")
    Result<List<PostDTO>> all();

    @GetMapping("/api/post/batch")
    Result<List<PostDTO>> batch(@RequestParam("ids") String ids);
}
