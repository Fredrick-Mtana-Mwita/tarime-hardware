package africa.finishers.reporting.api;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.reporting.application.ReportModels.*;
import africa.finishers.reporting.application.Reports;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/staff/reports")
public class ReportController {
    private final Reports reports;
    public ReportController(Reports reports){this.reports=reports;}
    private static StaffPrincipal actor(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof StaffPrincipal p))throw new AuthProblem(403,"Staff sign-in is required.");return p;
    }
    @GetMapping("/sales")
    public SalesReport sales(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return reports.sales(actor(auth),from,to);}
    @GetMapping("/balances")
    public BalancesReport balances(Authentication auth){return reports.balances(actor(auth));}
    @GetMapping("/stock")
    public StockReport stock(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return reports.stock(actor(auth),from,to);}

    @GetMapping("/sales.csv")
    public ResponseEntity<byte[]> salesCsv(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        var r=reports.sales(actor(auth),from,to);
        var out=new StringBuilder(Csv.row("Date","Requested order value","Completed sales (delivered or collected)","Money received","Refunds approved","Currency"));
        for(var d:r.days())out.append(Csv.row(d.date(),d.requestedValue(),d.completedSales(),d.received(),d.refunded(),r.currency()));
        out.append(Csv.row("Total",r.orders().requestedValue(),r.sales().gross(),r.money().received(),r.money().refunded(),r.currency()));
        return csv("sales-"+r.from()+"-to-"+r.to()+".csv",out);
    }
    @GetMapping("/products.csv")
    public ResponseEntity<byte[]> productsCsv(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        var r=reports.sales(actor(auth),from,to);
        var out=new StringBuilder(Csv.row("Product","Option","Unit","Quantity sold","Sales value","Currency"));
        for(var p:r.topProducts())out.append(Csv.row(p.product(),p.variant(),p.unit(),p.quantity(),p.value(),r.currency()));
        return csv("top-products-"+r.from()+"-to-"+r.to()+".csv",out);
    }
    @GetMapping("/balances.csv")
    public ResponseEntity<byte[]> balancesCsv(Authentication auth){
        var r=reports.balances(actor(auth));
        var out=new StringBuilder(Csv.row("Order","Customer","Phone","Status","Order total","Paid (net)","Customer owes","Shop owes back","Days open","Currency"));
        for(var b:r.rows())out.append(Csv.row(b.reference(),b.customer(),b.phone(),b.status(),b.total(),b.netPaid(),b.due(),b.refundDue(),b.ageDays(),r.currency()));
        return csv("balances-"+LocalDate.now(Reports.SHOP_ZONE)+".csv",out);
    }
    @GetMapping("/stock.csv")
    public ResponseEntity<byte[]> stockCsv(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){
        var r=reports.stock(actor(auth),from,to);
        var out=new StringBuilder(Csv.row("Product","Option","SKU","Unit","On hand","Reserved","Available","Low-stock level","Low stock","Unit price","Value at selling price","Received in period","Dispatched in period","Released in period","Currency"));
        for(var s:r.rows())out.append(Csv.row(s.product(),s.variant(),s.sku(),s.unit(),s.onHand(),s.reserved(),s.available(),s.threshold(),s.lowStock()?"Yes":"No",s.unitPrice(),s.valueAtPrice(),s.received(),s.dispatched(),s.released(),r.currency()));
        return csv("stock-"+r.branch().toLowerCase()+"-"+r.from()+"-to-"+r.to()+".csv",out);
    }
    /** UTF-8 with a byte-order mark so Excel shows Kiswahili and other text correctly. */
    private static ResponseEntity<byte[]> csv(String filename,StringBuilder body){
        byte[] text=("﻿"+body).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").header(HttpHeaders.CACHE_CONTROL,"no-store").body(text);
    }
}
