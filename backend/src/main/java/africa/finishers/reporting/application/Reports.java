package africa.finishers.reporting.application;

import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import africa.finishers.reporting.application.ReportModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class Reports {
    public static final ZoneId SHOP_ZONE=ZoneId.of("Africa/Dar_es_Salaam");
    public static final int MAX_DAYS=366;
    public interface Store {
        SalesReport sales(LocalDate from,LocalDate to);
        BalancesReport balances();
        StockReport stock(LocalDate from,LocalDate to);
    }
    private final StaffAccounts staff;
    private final Store store;
    public Reports(StaffAccounts staff,Store store){this.staff=staff;this.store=store;}

    @Transactional(readOnly=true)
    public SalesReport sales(StaffPrincipal actor,LocalDate from,LocalDate to){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        var range=range(from,to);return store.sales(range[0],range[1]);
    }
    @Transactional(readOnly=true)
    public BalancesReport balances(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER,StaffRole.SALES);return store.balances();}
    @Transactional(readOnly=true)
    public StockReport stock(StaffPrincipal actor,LocalDate from,LocalDate to){
        staff.require(actor,StaffRole.OWNER,StaffRole.INVENTORY);
        var range=range(from,to);return store.stock(range[0],range[1]);
    }
    /** Shop-time calendar days, both ends included. Defaults to the last 7 days. */
    static LocalDate[] range(LocalDate from,LocalDate to){
        LocalDate today=LocalDate.now(SHOP_ZONE);
        LocalDate end=to==null?today:to;
        LocalDate start=from==null?end.minusDays(6):from;
        if(start.isAfter(end))throw new AuthProblem(400,"The start date must be on or before the end date.");
        if(end.isAfter(today))throw new AuthProblem(400,"Choose dates up to today.");
        if(ChronoUnit.DAYS.between(start,end)+1>MAX_DAYS)throw new AuthProblem(400,"Choose a range of at most 366 days.");
        return new LocalDate[]{start,end};
    }
}
