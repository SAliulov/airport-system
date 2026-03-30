package ru.airport.service;

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
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.ScheduleRs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Экспорт итогового расписания на день (задача 6, FirstLab).
 */
@Service
@RequiredArgsConstructor
public class ScheduleExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FlightService flightService;

    public byte[] exportExcel(LocalDate date) throws IOException {
        List<FlightRs> rows = flightService.list(date, null, null, null);
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
            for (FlightRs f : rows) {
                Row row = sh.createRow(r++);
                fillRow(row, f);
            }
            for (int i = 0; i < headers.length; i++) {
                sh.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportPdf(LocalDate date) {
        List<FlightRs> rows = flightService.list(date, null, null, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);
        doc.add(new Paragraph("Airport schedule - " + date).setBold().setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(" "));

        float[] cols = {1.2f, 1f, 0.8f, 0.8f, 1.4f, 1.4f, 1.1f, 0.9f};
        Table table = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth();
        String[] headers = {"Flight", "Airline", "From", "To", "Departure", "Arrival", "Status", "Gate"};
        for (String h : headers) {
            table.addHeaderCell(headerCell(h));
        }
        for (FlightRs f : rows) {
            ScheduleRs s = f.getSchedule();
            table.addCell(pdfCell(s != null ? s.getFlightNumber() : ""));
            table.addCell(pdfCell(s != null && s.getAirline() != null ? s.getAirline().getIataCode() : ""));
            table.addCell(pdfCell(s != null ? s.getOriginAirport() : ""));
            table.addCell(pdfCell(s != null ? s.getDestinationAirport() : ""));
            table.addCell(pdfCell(s != null && s.getScheduledDeparture() != null ? FMT.format(s.getScheduledDeparture()) : ""));
            table.addCell(pdfCell(s != null && s.getScheduledArrival() != null ? FMT.format(s.getScheduledArrival()) : ""));
            table.addCell(pdfCell(f.getStatus() != null ? f.getStatus().name() : ""));
            GateAssignmentRs ga = f.getCurrentGateAssignment();
            String gate = "";
            if (ga != null && ga.getGate() != null) {
                gate = ga.getGate().getGateNumber();
            }
            table.addCell(pdfCell(gate));
        }
        doc.add(table);
        doc.close();
        return baos.toByteArray();
    }

    private static Cell headerCell(String text) {
        return new Cell()
                .add(new Paragraph(asciiPdfSafe(text)).setBold())
                .setTextAlignment(TextAlignment.CENTER);
    }

    /**
     * Стандартный шрифт PDF без встроенного TTF не рисует кириллицу и может бросить исключение.
     * Excel оставляем с полным UTF-16; для PDF — только печатный ASCII, остальное «?».
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

    private static Cell pdfCell(String text) {
        return new Cell().add(new Paragraph(asciiPdfSafe(text)));
    }

    private static void fillRow(Row row, FlightRs f) {
        ScheduleRs s = f.getSchedule();
        int c = 0;
        row.createCell(c++).setCellValue(s != null ? s.getFlightNumber() : "");
        row.createCell(c++).setCellValue(s != null && s.getAirline() != null ? s.getAirline().getIataCode() : "");
        row.createCell(c++).setCellValue(s != null ? s.getOriginAirport() : "");
        row.createCell(c++).setCellValue(s != null ? s.getDestinationAirport() : "");
        row.createCell(c++).setCellValue(s != null && s.getScheduledDeparture() != null ? FMT.format(s.getScheduledDeparture()) : "");
        row.createCell(c++).setCellValue(s != null && s.getScheduledArrival() != null ? FMT.format(s.getScheduledArrival()) : "");
        row.createCell(c++).setCellValue(f.getStatus() != null ? f.getStatus().name() : "");
        GateAssignmentRs ga = f.getCurrentGateAssignment();
        String gate = "";
        if (ga != null && ga.getGate() != null) {
            gate = ga.getGate().getGateNumber();
        }
        row.createCell(c).setCellValue(gate);
    }
}
