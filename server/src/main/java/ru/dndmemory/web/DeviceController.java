package ru.dndmemory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.dndmemory.security.UserPrincipal;
import ru.dndmemory.service.DeviceService;
import ru.dndmemory.web.dto.DeviceDtos;
import ru.dndmemory.web.dto.InviteDtos;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /** Шаг админа: QR-код для регистрации устройства. */
    @PostMapping("/api/campaigns/{campaignId}/devices/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteDtos.InviteDto createEnrollment(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID campaignId,
                                                 @Valid @RequestBody DeviceDtos.CreateEnrollmentRequest request) {
        return deviceService.createEnrollment(campaignId, principal.getId(), request);
    }

    /** Список устройств кампании — чтобы видеть, у кого есть доступ, и отзывать его. */
    @GetMapping("/api/campaigns/{campaignId}/devices")
    public List<DeviceDtos.DeviceDto> list(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID campaignId) {
        return deviceService.list(campaignId, principal.getId());
    }

    @DeleteMapping("/api/campaigns/{campaignId}/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID campaignId,
                       @PathVariable UUID deviceId) {
        deviceService.revoke(campaignId, deviceId, principal.getId());
    }

    /** Шаг телефона: единственный эндпоинт API, доступный без авторизации (кроме входа и health). */
    @PostMapping("/api/devices/enroll")
    public DeviceDtos.EnrollResponse enroll(@Valid @RequestBody DeviceDtos.EnrollRequest request) {
        return deviceService.enroll(request);
    }
}
