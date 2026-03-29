-- Согласование с ER-диаграммой и FirstLab.md: поле version в схеме не используется.
-- Конфликт занятости гейта предотвращается проверкой пересечения интервалов в gate_assignment (сервисный слой + GateAssignmentRepository.findOverlapping).
ALTER TABLE flight
    DROP COLUMN IF EXISTS version;
