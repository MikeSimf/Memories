package ru.dndmemory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Телефон — второй способ входа, поэтому разбор номера должен быть предсказуемым. */
class PhoneUtilsTest {

    @Test
    @DisplayName("Российские номера приводятся к +7XXXXXXXXXX в любом написании")
    void normalizesRussianNumbers() {
        assertEquals("+79991234567", PhoneUtils.normalize("8 999 123-45-67"));
        assertEquals("+79991234567", PhoneUtils.normalize("89991234567"));
        assertEquals("+79991234567", PhoneUtils.normalize("+7 (999) 123 45 67"));
        assertEquals("+79991234567", PhoneUtils.normalize("9991234567"));
    }

    @Test
    @DisplayName("Иностранный номер сохраняется с плюсом")
    void keepsInternationalNumber() {
        assertEquals("+4915112345678", PhoneUtils.normalize("+49 151 1234 5678"));
    }

    @Test
    @DisplayName("Почта и мусор телефоном не считаются")
    void doesNotMistakeEmailForPhone() {
        assertFalse(PhoneUtils.looksLikePhone("gm@example.com"));
        assertFalse(PhoneUtils.looksLikePhone("12345"));
        assertFalse(PhoneUtils.looksLikePhone(""));
        assertFalse(PhoneUtils.looksLikePhone(null));
        assertNull(PhoneUtils.normalize("gm@example.com"));
    }
}
