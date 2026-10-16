package ru.dndmemory.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.UUID;

/** Объект, у которого может быть картинка (портрет героя, вид локации, иконка предмета). */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class ImageExtender extends SyncExtender {

    @Column(name = "image_file_id")
    private UUID imageFileId;
}
