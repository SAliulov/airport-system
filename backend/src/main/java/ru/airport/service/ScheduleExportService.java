package ru.airport.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.ScheduleRs;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Flight;
import ru.airport.repository.FlightRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экспорт итогового расписания на день (задача 6, FirstLab).
 * Строки совпадают с {@code GET /api/v1/schedules/filter?date=} (тот же набор, что {@link ScheduleService#filter});
 * колонки «Status» и «Gate» подставляются из связанного {@code flight} за этот день, если запись есть;
 * иначе «—» (план есть, выполняемый рейс ещё не создан).
 * PDF: кириллица через {@code /fonts/NotoSans-Regular.ttf}; без файла — подстановка «?» для не-ASCII.
 * Требуется роль DISPATCHER (JWT Bearer); без заголовка Authorization — 401.
 */
/**
 * Экспорт операционного расписания на дату в PDF и Excel (только DISPATCHER).
 * Строки строятся из шаблонов с экземплярами flight на выбранный день.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PDF_FONT_RESOURCE = "/fonts/NotoSans-Regular.ttf";

    private final ScheduleService scheduleService;
    private final FlightRepository flightRepository;
    private final DtoMapper mapper;

    public byte[] exportExcel(LocalDate date) throws IOException {
        List<ScheduleRs> schedules = scheduleService.filter(date, null, null, null, null, null);
        Map<Integer, FlightRs> flightByScheduleId = indexFlightsForDay(date);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Flights");
            Row h = sh.createRow(0);
            String[] headers = {
                    "Flight", "Airline", "From", "To", "Departure", "Arrival", "Status", "Gate"
            };
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (ScheduleRs s : schedules) {
                Row row = sh.createRow(r++);
                fillRow(row, s, flightByScheduleId.get(s.getScheduleId()));
            }
            for (int i = 0; i < headers.length; i++) {
                sh.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportPdf(LocalDate date) {
        List<ScheduleRs> schedules = scheduleService.filter(date, null, null, null, null, null);
        Map<Integer, FlightRs> flightByScheduleId = indexFlightsForDay(date);
        PdfFont font = loadPdfBodyFont();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);
        doc.add(pdfParagraph("Расписание аэропорта на: " + date, font, true).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(" "));

        float[] cols = {1.2f, 1f, 0.8f, 0.8f, 1.4f, 1.4f, 1.1f, 0.9f};
        Table table = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth();
        String[] headers = {"Рейс", "Авиакомпания", "Откуда", "Куда", "Отправка", "Прибытие", "Статус", "Гейт"};
        for (String h : headers) {
            table.addHeaderCell(headerCell(h, font));
        }
        for (ScheduleRs s : schedules) {
            FlightRs f = flightByScheduleId.get(s.getScheduleId());
            table.addCell(pdfCell(s.getFlightNumber(), font));
            table.addCell(pdfCell(s.getAirline() != null ? s.getAirline().getIataCode() : "", font));
            table.addCell(pdfCell(s.getOriginAirport(), font));
            table.addCell(pdfCell(s.getDestinationAirport(), font));
            table.addCell(pdfCell(s.getDepartureAtDate() != null ? FMT.format(s.getDepartureAtDate()) : "", font));
            table.addCell(pdfCell(s.getArrivalAtDate() != null ? FMT.format(s.getArrivalAtDate()) : "", font));
            table.addCell(pdfCell(f != null && f.getStatus() != null ? f.getStatus().name() : "—", font));
            String gate = "";
            if (f != null && f.getCurrentGateAssignment() != null && f.getCurrentGateAssignment().getGate() != null) {
                gate = f.getCurrentGateAssignment().getGate().getGateNumber();
            }
            table.addCell(pdfCell(gate, font));
        }
        doc.add(table);
        doc.close();
        return baos.toByteArray();
    }

    /**
     * Один рейс на расписание за сутки; при нескольких {@code flight} на одно {@code schedule}
     * оставляем «богаче» данными (есть актуальный гейт, иначе больший {@code flightId}).
     */
    private Map<Integer, FlightRs> indexFlightsForDay(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Map<Integer, Flight> chosen = new LinkedHashMap<>();
        for (Flight fl : flightRepository.findByDay(start, end)) {
            Integer sid = fl.getSchedule().getScheduleId();
            chosen.merge(sid, fl, ScheduleExportService::preferRicherFlight);
        }
        Map<Integer, FlightRs> map = new LinkedHashMap<>();
        chosen.forEach((k, v) -> map.put(k, mapper.toFlightRsSummary(v)));
        return map;
    }

    private static Flight preferRicherFlight(Flight existing, Flight incoming) {
        boolean eGate = existing.getActiveGateAssignment() != null;
        boolean iGate = incoming.getActiveGateAssignment() != null;
        if (eGate != iGate) {
            return iGate ? incoming : existing;
        }
        int eid = existing.getFlightId() != null ? existing.getFlightId() : 0;
        int iid = incoming.getFlightId() != null ? incoming.getFlightId() : 0;
        return iid >= eid ? incoming : existing;
    }

    private PdfFont loadPdfBodyFont() {
        try (InputStream is = ScheduleExportService.class.getResourceAsStream(PDF_FONT_RESOURCE)) {
            if (is == null) {
                return null;
            }
            byte[] bytes = is.readAllBytes();
            return PdfFontFactory.createFont(
                    bytes,
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        } catch (Exception e) {
            return null;
        }
    }

    private static Paragraph pdfParagraph(String text, PdfFont font, boolean bold) {
        String t = text != null ? text : "";
        Paragraph p = new Paragraph(font != null ? t : asciiPdfSafe(t));
        if (font != null) {
            p.setFont(font);
        }
        if (bold) {
            p.setBold();
        }
        return p;
    }

    private static Cell headerCell(String text, PdfFont font) {
        return new Cell()
                .add(pdfParagraph(text, font, true))
                .setTextAlignment(TextAlignment.CENTER);
    }

    /**
     * Если TTF не загрузился, стандартный шрифт PDF не рисует кириллицу — оставляем печатный ASCII.
     */
    private static String asciiPdfSafe(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c >= 32 && c <= 126 ? c : '?');
        }
        return sb.toString();
    }

    private static Cell pdfCell(String text, PdfFont font) {
        String t = text != null ? text : "";
        return new Cell().add(pdfParagraph(t, font, false));
    }

    private static void fillRow(Row row, ScheduleRs s, FlightRs flightRow) {
        int c = 0;
        row.createCell(c++).setCellValue(s.getFlightNumber() != null ? s.getFlightNumber() : "");
        row.createCell(c++).setCellValue(s.getAirline() != null ? s.getAirline().getIataCode() : "");
        row.createCell(c++).setCellValue(s.getOriginAirport() != null ? s.getOriginAirport() : "");
        row.createCell(c++).setCellValue(s.getDestinationAirport() != null ? s.getDestinationAirport() : "");
        row.createCell(c++).setCellValue(s.getDepartureAtDate() != null ? FMT.format(s.getDepartureAtDate()) : "");
        row.createCell(c++).setCellValue(s.getArrivalAtDate() != null ? FMT.format(s.getArrivalAtDate()) : "");
        row.createCell(c++).setCellValue(
                flightRow != null && flightRow.getStatus() != null ? flightRow.getStatus().name() : "—");
        String gate = "";
        if (flightRow != null) {
            GateAssignmentRs ga = flightRow.getCurrentGateAssignment();
            if (ga != null && ga.getGate() != null) {
                gate = ga.getGate().getGateNumber();
            }
        }
        row.createCell(c).setCellValue(gate);
    }
}
