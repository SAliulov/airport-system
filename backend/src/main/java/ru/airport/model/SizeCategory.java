package ru.airport.model;

/**
 * Категория размера воздушного судна / максимальная категория гейта.
 * Используется для проверки совместимости при назначении гейта (задача 4/5).
 *
 * Порядок важен: NARROW < WIDE < JUMBO.
 * Гейт с max_size_category=WIDE принимает NARROW и WIDE, но не JUMBO.
 */
public enum SizeCategory {
    NARROW,
    WIDE,
    JUMBO;

    /**
     * Проверяет, помещается ли воздушное судно данной категории в гейт.
     *
     * @param aircraftCategory категория ВС
     * @param gateMax          максимальная категория гейта
     * @return true — совместимы
     */
    public static boolean isCompatible(SizeCategory aircraftCategory, SizeCategory gateMax) {
        return aircraftCategory.ordinal() <= gateMax.ordinal();
    }
}