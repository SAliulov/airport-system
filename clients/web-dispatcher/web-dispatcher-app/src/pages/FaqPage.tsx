export default function FaqPage() {
  return (
    <div className="page faq-page">
      <h1>Справка / FAQ</h1>
      <p className="faq-subtitle">
        Справочная информация по бизнес-логике, валидации и основным сценариям работы системы АСУРР.
      </p>

      <section className="faq-section">
        <h2>Справочники</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">A</span> Авиакомпании</summary>
          <div className="faq-body">
            <h4>Бизнес-правила</h4>
            <ul>
              <li><strong>Уникальность IATA-кода</strong> — код авиакомпании (2 символа) должен быть уникальным в системе. При попытке создать или обновить запись с дублирующимся кодом возвращается ошибка 409 Conflict.</li>
              <li><strong>Защита удаления</strong> — авиакомпанию нельзя удалить, если на неё ссылаются шаблоны расписания (<code>schedule</code>). При попытке удаления возвращается ошибка 409 Conflict.</li>
            </ul>
            <h4>Поля формы</h4>
            <ul>
              <li><code>iataCode</code> — двухбуквенный код IATA (обязательное поле, проверка на уникальность).</li>
              <li><code>name</code> — полное наименование авиакомпании.</li>
              <li><code>country</code> — страна регистрации (необязательно).</li>
            </ul>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">C</span> Типы ВС</summary>
          <div className="faq-body">
            <h4>Бизнес-правила</h4>
            <ul>
              <li><strong>Уникальность ICAO-кода</strong> — код типа воздушного судна (4 символа) должен быть уникальным. Дубликаты вызывают ошибку 409 Conflict.</li>
              <li><strong>Неизменяемость sizeCategory</strong> — если тип ВС уже используется в рейсах, категорию размера (<code>NARROW</code>, <code>WIDE</code>, <code>JUMBO</code>) изменить нельзя.</li>
              <li><strong>Защита удаления</strong> — тип ВС нельзя удалить, если он назначен хотя бы одному рейсу.</li>
            </ul>
            <h4>Категории размера</h4>
            <table className="data-table faq-table">
              <thead>
                <tr><th>Категория</th><th>Описание</th></tr>
              </thead>
              <tbody>
                <tr><td>NARROW</td><td>Узкофюзеляжные (A320, B737)</td></tr>
                <tr><td>WIDE</td><td>Широкофюзеляжные (B777, A330)</td></tr>
                <tr><td>JUMBO</td><td>Двухпалубные (A380, B747)</td></tr>
              </tbody>
            </table>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">G</span> Гейты</summary>
          <div className="faq-body">
            <h4>Бизнес-правила</h4>
            <ul>
              <li><strong>Уникальность номера</strong> — номер гейта должен быть уникальным. Дубликаты вызывают ошибку 409 Conflict.</li>
              <li><strong>Защита удаления</strong> — гейт нельзя удалить, если на нём есть назначения (<code>gate_assignment</code>).</li>
              <li><strong>Совместимость с ВС</strong> — при назначении гейта проверяется совместимость размера ВС с максимально допустимой категорией гейта (<code>maxSizeCategory</code>).</li>
              <li><strong>Активность гейта</strong> — назначение возможно только на активный гейт (<code>isActive = true</code>).</li>
            </ul>
            <h4>Поля</h4>
            <ul>
              <li><code>gateNumber</code> — номер гейта (уникальный).</li>
              <li><code>terminal</code> — терминал (A, B, C, D, E, F).</li>
              <li><code>isActive</code> — флаг активности гейта.</li>
              <li><code>maxSizeCategory</code> — максимальная категория ВС, которую может принять гейт.</li>
            </ul>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Плановое расписание (Шаблоны)</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">S</span> Шаблон расписания — общие правила</summary>
          <div className="faq-body">
            <h4>Жизненный цикл</h4>
            <ul>
              <li><strong>Сезонность</strong> — шаблон действует в интервале <code>[effectiveFrom, effectiveTo]</code>. Если <code>effectiveTo</code> не указан, шаблон бессрочный.</li>
              <li><strong>Активность</strong> — только активные шаблоны (<code>isActive = true</code>) участвуют в генерации экземпляров рейсов.</li>
              <li><strong>Защита удаления</strong> — шаблон нельзя удалить, если на него ссылаются экземпляры рейсов (<code>flight</code>).</li>
              <li><strong>Обновление при наличии рейсов</strong> — если на шаблон есть связанные рейсы, нельзя менять: маршрут (origin/destination), дату начала периода, тип периодичности, шаг периодичности, а также сокращать <code>effectiveTo</code>. Деактивация (<code>isActive = false</code>) блокируется, если есть рейсы со статусами SCHEDULED, DEPARTED, DELAYED.</li>
            </ul>
            <h4>Параметры периодичности</h4>
            <ul>
              <li><code>periodicityType</code> — <strong>WEEKLY</strong> (еженедельно) или <strong>INTERVAL</strong> (с календарным интервалом).</li>
              <li><code>periodicityStep</code> — шаг &ge; 1. Для WEEKLY: каждая N-я неделя. Для INTERVAL: каждые N календарных дней.</li>
            </ul>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">T</span> Типы периодичности</summary>
          <div className="faq-body">
            <h4>WEEKLY (Еженедельно)</h4>
            <ul>
              <li>Шаблон содержит один или несколько слотов, каждый с указанием дня недели (1 = Пн … 7 = Вс).</li>
              <li>Дни недели в рамках одного шаблона не должны повторяться (partial UNIQUE).</li>
              <li>Условие совпадения: день недели даты совпадает с <code>dayOfWeek</code> слота И <code>ChronoUnit.WEEKS.between(effectiveFrom, date) % step == 0</code>.</li>
            </ul>
            <h4>INTERVAL (Интервальный)</h4>
            <ul>
              <li>Шаблон содержит ровно один слот, у которого <code>dayOfWeek = null</code>.</li>
              <li>Условие совпадения: <code>ChronoUnit.DAYS.between(effectiveFrom, date) % step == 0</code> (день недели не учитывается).</li>
              <li>Пример: step=3 — рейс выполняется каждые 3 календарных дня, начиная с <code>effectiveFrom</code>.</li>
            </ul>
            <p className="faq-note"><strong>Важно:</strong> INTERVAL step=7 и WEEKLY step=1 — это разные вещи. Первый отсчитывает календарные дни, второй — ISO-недели от якоря.</p>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">D</span> Слоты шаблона и сценарий A</summary>
          <div className="faq-body">
            <h4>Валидация слотов</h4>
            <ul>
              <li>Минимум один слот обязателен.</li>
              <li>WEEKLY: <code>dayOfWeek</code> в диапазоне [1, 7], без дубликатов. <code>departureTime</code> и <code>arrivalTime</code> — время (HH:mm), не равны друг другу.</li>
              <li>INTERVAL: ровно один слот, <code>dayOfWeek = null</code>.</li>
            </ul>
            <h4>Сценарий A — DOW вне периода</h4>
            <p>Если день недели слота <strong>вообще не встречается</strong> в интервале <code>[effectiveFrom, effectiveTo]</code>, сохранение шаблона отклоняется с ошибкой 400.</p>
            <p className="faq-example">Пример: effectiveFrom = Пн, effectiveTo = Ср, dayOfWeek = Вс → ошибка. Интервал слишком короткий для указанного дня недели.</p>
            <p className="faq-note"><strong>На фронтенде</strong>: при таком сценарии на поле слота отображается сообщение: «Дружище, ты выбрал период с Пн по Ср, какое к черту Воскресенье?»</p>
            <h4>Сценарий B — DOW есть, но шаг глушит</h4>
            <p>Если DOW присутствует в диапазоне, но шаг периодичности «гасит» все возможные даты — сохранение проходит успешно. Генерация экземпляров вернёт <code>created: 0, skipped: 0</code>, что является штатным поведением.</p>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">O</span> Overnight-рейсы</summary>
          <div className="faq-body">
            <p>Если <code>arrivalTime &le; departureTime</code>, прибытие считается на следующий календарный день:</p>
            <ul>
              <li><code>scheduled_departure</code> = operationDate + departureTime</li>
              <li><code>scheduled_arrival</code> = operationDate + arrivalTime + 1 день</li>
            </ul>
            <p className="faq-example">Пример: вылет в 23:00, прибытие в 06:00 → прибытие на следующий день.</p>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Экземпляры рейсов</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">F</span> Статусы и переходы</summary>
          <div className="faq-body">
            <h4>Доступные статусы</h4>
            <div className="faq-status-chart">
              <div className="faq-status-item"><span className="badge">SCHEDULED</span> — запланирован</div>
              <div className="faq-status-item"><span className="badge">DELAYED</span> — задержан</div>
              <div className="faq-status-item"><span className="badge">DEPARTED</span> — вылетел</div>
              <div className="faq-status-item"><span className="badge">ARRIVED</span> — прибыл</div>
              <div className="faq-status-item"><span className="badge">CANCELLED</span> — отменён</div>
            </div>

            <h4>Ручные переходы (диспетчер)</h4>
            <table className="data-table faq-table">
              <thead>
                <tr><th>Из статуса</th><th>В статус</th><th>Условия</th></tr>
              </thead>
              <tbody>
                <tr><td>SCHEDULED</td><td>DEPARTED</td><td>Вылетающий: facetime, ВС, гейт; прибывающий: facetime, ВС</td></tr>
                <tr><td>SCHEDULED</td><td>DELAYED</td><td>-</td></tr>
                <tr><td>SCHEDULED</td><td>CANCELLED</td><td>-</td></tr>
                <tr><td>DELAYED</td><td>DEPARTED</td><td>Вылетающий: facetime, ВС, гейт</td></tr>
                <tr><td>DELAYED</td><td>CANCELLED</td><td>-</td></tr>
                <tr><td>DELAYED</td><td>ARRIVED</td><td>Только для прибывающих рейсов</td></tr>
                <tr><td>DEPARTED</td><td>ARRIVED</td><td>Прибывающий: facetime, ВС, гейт; вылетающий: facetime, ВС, facetime прилёта</td></tr>
                <tr><td>ARRIVED / CANCELLED</td><td>—</td><td>Конечные статусы, переходы невозможны</td></tr>
              </tbody>
            </table>

            <h4>Автоматические переходы (планировщик, каждую минуту)</h4>
            <table className="data-table faq-table">
              <thead>
                <tr><th>Условие</th><th>Новый статус</th></tr>
              </thead>
              <tbody>
                <tr><td>SCHEDULED + прошло grace-время после планового вылета</td><td>DELAYED (outbound) / DEPARTED (inbound с ВС)</td></tr>
                <tr><td>SCHEDULED + прошло время отмены после планового вылета</td><td>CANCELLED</td></tr>
                <tr><td>DELAYED + прошло время отмены</td><td>CANCELLED</td></tr>
                <tr><td>DEPARTED + прошло ожидаемое время прибытия</td><td>ARRIVED</td></tr>
                <tr><td>DEPARTED + нет гейта + прошло grace-время</td><td>DELAYED</td></tr>
              </tbody>
            </table>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">M</span> Мутация рейсов</summary>
          <div className="faq-body">
            <h4>Редактирование</h4>
            <ul>
              <li><strong>Запрещено</strong> редактировать рейсы в статусе <code>ARRIVED</code>.</li>
              <li><strong>Неизменяемые поля</strong>: <code>slotId</code> и <code>operationDate</code> — после создания рейса их изменить нельзя.</li>
              <li><strong>Ресурсы (тип ВС)</strong> можно менять только для статусов <code>SCHEDULED</code>, <code>DELAYED</code>.</li>
              <li><strong>Фактическое время</strong> можно задать для рейсов в статусе <code>SCHEDULED</code> или <code>DELAYED</code> прямо в модалке редактирования (поля «Фактическое время вылета» / «Фактическое время прилёта»). Для <code>ARRIVED</code> — только через эндпоинт коррекции.</li>
            </ul>
            <h4>Удаление</h4>
            <ul>
              <li>Рейс можно удалить только в статусах <code>SCHEDULED</code> или <code>CANCELLED</code>.</li>
            </ul>
            <h4>Управление гейтом</h4>
            <ul>
              <li>Нельзя сменить гейт для <code>CANCELLED</code> или <code>ARRIVED</code>.</li>
              <li>Для <code>DEPARTED</code> inbound (прибывающего) рейса гейт менять можно.</li>
            </ul>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">G</span> Генерация экземпляров</summary>
          <div className="faq-body">
            <h4>Эндпоинт</h4>
            <p><code>POST /api/v1/flights/generate</code> — создаёт экземпляры рейсов из шаблонов за указанный период.</p>
            <h4>Параметры запроса</h4>
            <ul>
              <li><code>fromDate</code> — начало периода генерации (обязательно, не ранее сегодня).</li>
              <li><code>toDate</code> — конец периода генерации.</li>
              <li><code>scheduleId</code> — опционально, ID конкретного шаблона для генерации.</li>
            </ul>
            <h4>Логика</h4>
            <ul>
              <li>Для каждой даты в диапазоне и каждого слота шаблона проверяется <code>matchesOperationDate</code>.</li>
              <li>Если экземпляр для пары <code>(slotId, date)</code> уже существует — пропускается.</li>
              <li>Результат <code>created: 0, skipped: 0</code> — штатная ситуация, если нет подходящих дат, это не ошибка.</li>
            </ul>
            <h4>Ручное создание</h4>
            <p><code>POST /api/v1/flights</code> — создание отдельного рейса с указанием <code>slotId</code> и <code>operationDate</code>. Сервер проверяет периодичность и запрещает backdating (дата в прошлом).</p>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">H</span> Домашний аэропорт и направление</summary>
          <div className="faq-body">
            <h4>Определение направления</h4>
            <ul>
              <li><strong>DEPARTURE (Вылетающий)</strong> — рейс, у которого <code>origin = homeIata</code> (домашний аэропорт из конфига).</li>
              <li><strong>ARRIVAL (Прибывающий)</strong> — рейс, у которого <code>destination = homeIata</code>.</li>
              <li>Маршрут обязательно должен проходить через домашний аэропорт хотя бы с одной стороны.</li>
            </ul>
            <h4>Требования для перехода в DEPARTED</h4>
            <ul>
              <li><strong>Outbound (вылетающий):</strong> фактическое время вылета, назначенный тип ВС, активное назначение гейта.</li>
              <li><strong>Inbound (прибывающий):</strong> фактическое время вылета и назначенный тип ВС (без гейта).</li>
            </ul>
            <h4>Требования для перехода в ARRIVED</h4>
            <ul>
              <li><strong>Inbound (прибывающий):</strong> фактическое время прибытия, тип ВС, активный гейт.</li>
              <li><strong>Outbound (вылетающий):</strong> фактическое время прибытия и тип ВС.</li>
            </ul>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">C</span> Фактическое время и коррекция</summary>
          <div className="faq-body">
            <h4>Валидация при установке actual_*</h4>
            <ul>
              <li>Фактическое время не может быть слишком далеко в будущем (настраиваемый лимит <code>maxActualTimeFutureSkewMinutes</code>).</li>
              <li>Фактический вылет: в пределах <code>[scheduled - maxEarlyHours, scheduled + maxLateHours]</code>.</li>
              <li>Фактическое прибытие: в пределах <code>[scheduled - maxEarlyHours, scheduled + maxLateHours]</code>.</li>
              <li>Прибытие не может быть раньше вылета (хронологический порядок).</li>
            </ul>
            <h4>Установка фактического времени при редактировании</h4>
            <ul>
              <li>Для рейсов в статусе <code>SCHEDULED</code> или <code>DELAYED</code> диспетчер может указать фактическое время вылета и/или прилёта прямо в модальном окне редактирования рейса.</li>
              <li>Для outbound: указание фактического времени вылета (при наличии гейта и типа ВС) позволяет планировщику перевести рейс в <code>DEPARTED</code>.</li>
              <li>Для inbound: указание фактического времени прибытия (при наличии гейта) переводит рейс в <code>ARRIVED</code>.</li>
            </ul>
            <h4>Коррекция (PUT /flights/&#123;id&#125;/actual-times)</h4>
            <ul>
              <li>Доступна только для рейсов в статусе <code>ARRIVED</code>.</li>
              <li>Оба поля (<code>actualDeparture</code>, <code>actualArrival</code>) не могут быть null одновременно.</li>
            </ul>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Назначение гейтов</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">G</span> Интервалы и пересечения</summary>
          <div className="faq-body">
            <h4>Правила назначения</h4>
            <ul>
              <li><strong>Интервал</strong> — обязательные поля <code>assignedFrom</code> и <code>assignedTo</code>. Начало не может быть в прошлом. Конец должен быть строго позже начала.</li>
              <li><strong>Пересечения</strong> — интервалы назначения на одном гейте не должны пересекаться. При попытке создать пересекающееся назначение — ошибка 409 Conflict.</li>
              <li><strong>Окно планирования</strong> — назначение должно перекрывать плановое время рейса с учётом настраиваемого окна (<code>planWindowHours</code>).</li>
              <li><strong>Совместимость ВС</strong> — размер ВС (NARROW/WIDE/JUMBO) должен быть совместим с <code>maxSizeCategory</code> гейта.</li>
              <li><strong>Активность гейта</strong> — назначение возможно только на активный гейт.</li>
            </ul>
            <h4>Привязка фактического времени</h4>
            <ul>
              <li>Фактическое время вылета/прибытия должно укладываться в интервал занятия гейта.</li>
              <li>Допускается выход за интервал на grace-период (<code>postGraceMinutes</code>).</li>
            </ul>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Задержки (Delay Warnings)</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">D</span> Предупреждения о задержке</summary>
          <div className="faq-body">
            <h4>Бизнес-правила</h4>
            <ul>
              <li><strong>Ручное создание</strong> — предупреждение о задержке можно добавить только для рейса в статусе <code>DELAYED</code>.</li>
              <li><strong>Автоматические задержки</strong> — генерируются планировщиком при авто-переходе в DELAYED с расчётом минут задержки от планового времени.</li>
              <li>WebSocket-уведомление отправляется на топик <code>/topic/delays</code>.</li>
            </ul>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Автоматические статусы</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">A</span> Работа планировщика</summary>
          <div className="faq-body">
            <h4>Периодичность</h4>
            <p>Планировщик запускается каждую минуту (<code>@Scheduled</code>) и выполняет автоматические переходы статусов.</p>

            <h4>Сценарии авто-переходов</h4>
            <table className="data-table faq-table">
              <thead>
                <tr><th>Сценарий</th><th>Исходный статус</th><th>Условие</th></tr>
              </thead>
              <tbody>
                <tr><td>Auto Cancel</td><td>SCHEDULED / DELAYED</td><td>Нет actualDeparture; прошло <code>cancelHoursAfterScheduledDeparture</code></td></tr>
                <tr><td>Auto Delay (Outbound)</td><td>SCHEDULED</td><td>Прошло <code>graceMinutes</code> после планового вылета</td></tr>
                <tr><td>Auto Departure (Inbound)</td><td>SCHEDULED / DELAYED</td><td>Нет actualDeparture; назначен тип ВС</td></tr>
                <tr><td>Auto Arrival (Outbound)</td><td>DEPARTED</td><td>Есть actualDeparture; прошло ожидаемое время прибытия</td></tr>
                <tr><td>Auto Delay (Inbound, missed dep.)</td><td>SCHEDULED</td><td>Нет actualDeparture; нет ВС; прошло grace-время</td></tr>
                <tr><td>Auto Delay (Inbound, no gate)</td><td>DEPARTED</td><td>Нет гейта; прошло grace-время после планового прибытия</td></tr>
              </tbody>
            </table>

            <h4>Изменяемые параметры (application.yml)</h4>
            <ul>
              <li><code>airport.scheduler.grace-minutes</code> — минут после планового времени для авто-задержки.</li>
              <li><code>airport.scheduler.cancel-hours-after-scheduled-departure</code> — часов ожидания перед авто-отменой.</li>
              <li><code>airport.scheduler.max-departure-early-hours</code> / <code>max-departure-late-hours</code> — границы для фактического вылета.</li>
              <li><code>airport.scheduler.max-arrival-early-hours</code> / <code>max-arrival-late-hours</code> — границы для фактического прибытия.</li>
            </ul>
            <p className="faq-note"><strong>Важно:</strong> автоматические статусы не публикуются в операционную ленту — только ручные действия диспетчера (через <code>ServiceAuditAspect</code>).</p>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Использование API</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">E</span> Экспорт расписания</summary>
          <div className="faq-body">
            <ul>
              <li><code>GET /api/v1/schedules/export/pdf?date=</code> — экспорт расписания на день в PDF (iText). Только для DISPATCHER.</li>
              <li><code>GET /api/v1/schedules/export/excel?date=</code> — экспорт в Excel (Apache POI). Только для DISPATCHER.</li>
            </ul>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">W</span> WebSocket-уведомления</summary>
          <div className="faq-body">
            <p>Система использует STOMP через SockJS на эндпоинте <code>/ws</code>. Доступные топики:</p>
            <table className="data-table faq-table">
              <thead>
                <tr><th>Топик</th><th>Назначение</th></tr>
              </thead>
              <tbody>
                <tr><td><code>/topic/flights</code></td><td>Обновления рейсов (статусы, назначения)</td></tr>
                <tr><td><code>/topic/delays</code></td><td>Новые предупреждения о задержках</td></tr>
                <tr><td><code>/topic/gate-changes</code></td><td>Изменения в назначениях гейтов</td></tr>
                <tr><td><code>/topic/operational-events</code></td><td>События операционной ленты</td></tr>
              </tbody>
            </table>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">A</span> Аутентификация и роли</summary>
          <div className="faq-body">
            <ul>
              <li><strong>JWT Bearer (HMAC-SHA256)</strong> — все защищённые эндпоинты требуют токен в заголовке <code>Authorization: Bearer &lt;token&gt;</code>.</li>
              <li><strong>Роль DISPATCHER</strong> — полный доступ ко всем мутациям, генерации и экспорту.</li>
              <li><strong>Роль READ_ONLY</strong> — только GET-запросы (табло, мобильные клиенты).</li>
              <li><strong>Blacklist</strong> — при logout токен добавляется в blacklist, повторное использование блокируется.</li>
            </ul>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Use Cases (сценарии)</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">1</span> Создание шаблона с генерацией рейсов</summary>
          <div className="faq-body">
            <ol>
              <li>Создать авиакомпанию (если отсутствует).</li>
              <li>Создать шаблон расписания <code>POST /schedules</code> с указанием маршрута, типа и шага периодичности, слотов.</li>
              <li>Убедиться, что DOW слотов попадает в период действия (иначе ошибка 400 — сценарий A).</li>
              <li>Выполнить генерацию <code>POST /flights/generate</code> с диапазоном дат.</li>
              <li>Просмотреть созданные экземпляры в разделе «Рейсы».</li>
            </ol>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">2</span> Обработка задержки рейса</summary>
          <div className="faq-body">
            <ol>
              <li>Диспетчер переводит рейс в статус <code>DELAYED</code> (<code>PUT /flights/&#123;id&#125;/status</code>).</li>
              <li>Добавляет предупреждение о задержке (<code>POST /flights/&#123;id&#125;/delay-warnings</code>) с указанием причины и минут.</li>
              <li>При необходимости назначает новый гейт (<code>POST /flights/&#123;id&#125;/gate-assignment</code>).</li>
              <li>После готовности — переводит в <code>DEPARTED</code>.</li>
            </ol>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">3</span> Bulk-удаление рейсов по шаблону</summary>
          <div className="faq-body">
            <p>Эндпоинт <code>DELETE /api/v1/flights/by-schedule/&#123;scheduleId&#125;</code> удаляет все экземпляры, связанные с указанным шаблоном. Подходит для очистки перед изменением параметров шаблона.</p>
          </div>
        </details>

        <details className="faq-card">
          <summary><span className="faq-icon">4</span> Просмотр таймлайна гейтов</summary>
          <div className="faq-body">
            <p><code>GET /api/v1/gates/timeline?date=</code> — отображает занятость всех гейтов на выбранную дату. Визуально показывает пересечения и свободные окна.</p>
          </div>
        </details>
      </section>

      <section className="faq-section">
        <h2>Ошибки и коды ответов</h2>

        <details className="faq-card" open>
          <summary><span className="faq-icon">!</span> Коды HTTP-ответов</summary>
          <div className="faq-body">
            <table className="data-table faq-table">
              <thead>
                <tr><th>Код</th><th>Описание</th><th>Типичная причина</th></tr>
              </thead>
              <tbody>
                <tr><td>200</td><td>Успех</td><td>GET, PUT, DELETE</td></tr>
                <tr><td>201</td><td>Создано</td><td>POST</td></tr>
                <tr><td>400</td><td>Bad Request</td><td>Нарушение бизнес-правил, невалидные данные, backdating</td></tr>
                <tr><td>401</td><td>Unauthorized</td><td>Отсутствует или истёк JWT токен</td></tr>
                <tr><td>403</td><td>Forbidden</td><td>Недостаточно прав (не DISPATCHER)</td></tr>
                <tr><td>404</td><td>Not Found</td><td>Сущность не найдена</td></tr>
                <tr><td>409</td><td>Conflict</td><td>Нарушение уникальности, пересечение интервалов гейтов, конфликт статусов</td></tr>
              </tbody>
            </table>
          </div>
        </details>
      </section>
    </div>
  );
}
