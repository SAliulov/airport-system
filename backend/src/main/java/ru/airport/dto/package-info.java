/**
 * Data Transfer Objects для REST API ({@code /api/v1/...}), см. AGENTS.md §8.
 * <ul>
 *   <li><b>*Rq</b> — тело запроса (POST/PUT); валидация через {@code @Valid} (§9).</li>
 *   <li><b>*Rs</b> — тело ответа; без JPA-прокси и лишних графов связей.</li>
 * </ul>
 * У каждого класса в Javadoc указаны <b>задачи из §6</b> и соответствующие эндпоинты.
 */
package ru.airport.dto;
