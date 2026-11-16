package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Квест кампании и комментарии к нему. */
public class QuestDto {
    public String id;
    public String name;
    public String description;
    public String avatarFileId;
    public String updatedAt;
    public List<Comment> comments = new ArrayList<>();

    public static class Comment {
        public String id;
        public String text;
        public String createdByName;
        public String createdAt;
    }
}
