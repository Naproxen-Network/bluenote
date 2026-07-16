package com.littlebluenote.common.dto;

import java.io.Serializable;

/** Post projection shared across services via OpenFeign. */
public class PostDTO implements Serializable {
    private Long id;
    private Long authorId;
    private String field;
    private String content;
    private String tags;
    private String image;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Integer viewCount;

    // hydrated author info (filled by recommend-service)
    private String authorName;
    private String authorAvatar;
    private String authorPosition;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorAvatar() { return authorAvatar; }
    public void setAuthorAvatar(String authorAvatar) { this.authorAvatar = authorAvatar; }
    public String getAuthorPosition() { return authorPosition; }
    public void setAuthorPosition(String authorPosition) { this.authorPosition = authorPosition; }
}
