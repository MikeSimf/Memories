package ru.dndmemory.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** POST /api/campaigns/{id}/sync/changes */
public class PushRequest {

    /** Идентификатор установки приложения. */
    public String clientId;

    public List<PushChangeDto> changes = new ArrayList<>();

    public PushRequest(String deviceClientId) {
        this.clientId = deviceClientId;
    }
}
