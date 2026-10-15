package ru.dndmemory.data.remote.dto;

/** POST /api/files → 201 */
public class FileDto {

    public String id;
    public String url;
    public String contentType;
    public long size;
}
