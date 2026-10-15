package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Ответ push. */
public class PushResponse {

    public long latestRevision;
    public List<PushResultDto> results = new ArrayList<>();
}
