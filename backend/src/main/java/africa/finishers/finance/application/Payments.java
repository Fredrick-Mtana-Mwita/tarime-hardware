package africa.finishers.finance.application;

import africa.finishers.finance.application.FinanceModels.*;
import africa.finishers.identity.application.AuthProblem;
import africa.finishers.identity.application.CustomerAccounts;
import africa.finishers.identity.application.StaffAccounts;
import africa.finishers.identity.application.StaffPrincipal;
import africa.finishers.identity.domain.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sales and Owner record payments and request corrections or refunds; only the Owner approves them (C2, C4, FR-19).
 * Inventory and Delivery staff cannot reach any of this.
 */
@Service
public class Payments {
    public static final ZoneId SHOP_ZONE=ZoneId.of("Africa/Dar_es_Salaam");
    private static final Pattern REFERENCE=Pattern.compile("[A-Z0-9]{6,30}");
    public interface Store {
        void record(UUID orderId,UUID actor,PaymentRequest request);
        void request(UUID orderId,UUID actor,AdjustmentRequest request);
        void approve(UUID adjustmentId,UUID actor,String reference,String note);
        void reject(UUID adjustmentId,UUID actor,String note);
        java.util.Optional<Ledger> ledger(UUID orderId);
        Page list(String query,boolean outstanding,int page,int size);
        List<Adjustment> pending();
        DaySummary summary(LocalDate date);
        java.util.Optional<CustomerPayments> forCustomer(UUID customerId,UUID orderId);
    }
    private final StaffAccounts staff;
    private final CustomerAccounts customers;
    private final Store store;
    public Payments(StaffAccounts staff,CustomerAccounts customers,Store store){this.staff=staff;this.customers=customers;this.store=store;}

    @Transactional(timeout=20)
    public Ledger record(StaffPrincipal actor,UUID orderId,PaymentRequest raw){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        if(raw==null||raw.id()==null||raw.method()==null)throw new AuthProblem(400,"Choose cash or mobile money and enter the amount.");
        BigDecimal amount=amount(raw.amount());
        String reference=null;Provider provider=null;
        if(raw.method()==Method.MOBILE_MONEY){
            provider=raw.provider();reference=reference(raw.reference());
            if(provider==null)throw new AuthProblem(400,"Choose the mobile money service.");
        }else if(raw.provider()!=null||(raw.reference()!=null&&!raw.reference().isBlank()))
            throw new AuthProblem(400,"A cash payment has no mobile money service or transaction ID.");
        String note=text(raw.note(),300,"Keep the note under 300 characters.");
        store.record(orderId,actor.id(),new PaymentRequest(raw.id(),raw.method(),provider,reference,amount,note));
        return ledger(orderId);
    }
    @Transactional(timeout=20)
    public Ledger request(StaffPrincipal actor,UUID orderId,AdjustmentRequest raw){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        if(raw==null||raw.id()==null||raw.kind()==null)throw new AuthProblem(400,"Choose a correction or a refund.");
        String reason=raw.reason()==null?"":raw.reason().strip();
        if(reason.length()<10||reason.length()>500)throw new AuthProblem(400,"Explain the reason in 10 to 500 characters.");
        AdjustmentRequest clean;
        if(raw.kind()==Kind.REVERSAL){
            if(raw.paymentId()==null)throw new AuthProblem(400,"Choose the receipt to cancel.");
            clean=new AdjustmentRequest(raw.id(),Kind.REVERSAL,raw.paymentId(),null,reason,null,null);
        }else{
            if(raw.paymentId()!=null)throw new AuthProblem(400,"A refund is for the order, not one receipt.");
            if(raw.refundMethod()==null)throw new AuthProblem(400,"Choose how the money goes back: cash or mobile money.");
            if((raw.refundMethod()==Method.MOBILE_MONEY)!=(raw.refundProvider()!=null))throw new AuthProblem(400,"Choose the mobile money service for a mobile money refund, and none for cash.");
            clean=new AdjustmentRequest(raw.id(),Kind.REFUND,null,amount(raw.amount()),reason,raw.refundMethod(),raw.refundProvider());
        }
        store.request(orderId,actor.id(),clean);
        return ledger(orderId);
    }
    @Transactional(timeout=20)
    public void approve(StaffPrincipal actor,UUID adjustmentId,Decision decision){
        staff.require(actor,StaffRole.OWNER);
        String reference=decision==null||decision.reference()==null||decision.reference().isBlank()?null:reference(decision.reference());
        store.approve(adjustmentId,actor.id(),reference,text(decision==null?null:decision.note(),300,"Keep the note under 300 characters."));
    }
    @Transactional(timeout=20)
    public void reject(StaffPrincipal actor,UUID adjustmentId,Decision decision){
        staff.require(actor,StaffRole.OWNER);
        String note=text(decision==null?null:decision.note(),300,"Keep the note under 300 characters.");
        if(note==null||note.length()<5)throw new AuthProblem(400,"Tell the requester why, in at least 5 characters.");
        store.reject(adjustmentId,actor.id(),note);
    }
    @Transactional(readOnly=true)
    public Ledger ledger(StaffPrincipal actor,UUID orderId){staff.require(actor,StaffRole.OWNER,StaffRole.SALES);return ledger(orderId);}
    @Transactional(readOnly=true)
    public Page list(StaffPrincipal actor,String query,String filter,int page,int size){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        String q=query==null?"":query.strip();
        if(q.length()>100||page<0||page>10000||size<1||size>50||!List.of("OUTSTANDING","ALL").contains(filter))throw new AuthProblem(400,"Check the search, filter and page.");
        return store.list(q.toLowerCase(Locale.ROOT),filter.equals("OUTSTANDING"),page,size);
    }
    @Transactional(readOnly=true)
    public List<Adjustment> pending(StaffPrincipal actor){staff.require(actor,StaffRole.OWNER,StaffRole.SALES);return store.pending();}
    @Transactional(readOnly=true)
    public DaySummary summary(StaffPrincipal actor,LocalDate date){
        staff.require(actor,StaffRole.OWNER,StaffRole.SALES);
        LocalDate today=LocalDate.now(SHOP_ZONE);
        LocalDate day=date==null?today:date;
        if(day.isAfter(today)||day.isBefore(today.minusYears(10)))throw new AuthProblem(400,"Choose a date up to today.");
        return store.summary(day);
    }
    @Transactional(readOnly=true)
    public CustomerPayments forCustomer(String principal,UUID orderId){
        return store.forCustomer(customers.profile(principal).id(),orderId).orElseThrow(()->new AuthProblem(404,"Order not found."));
    }
    private Ledger ledger(UUID orderId){return store.ledger(orderId).orElseThrow(()->new AuthProblem(404,"Order not found."));}
    private static BigDecimal amount(BigDecimal value){
        if(value==null||value.signum()<=0||value.scale()>2||value.precision()-value.scale()>17)throw new AuthProblem(400,"Enter an amount above zero with at most 2 decimal places.");
        return value.setScale(2);
    }
    private static String reference(String value){
        String clean=value==null?"":value.strip().toUpperCase(Locale.ROOT);
        if(!REFERENCE.matcher(clean).matches())throw new AuthProblem(400,"Enter the mobile money transaction ID: 6 to 30 letters and numbers.");
        return clean;
    }
    private static String text(String value,int max,String message){
        if(value==null||value.isBlank())return null;
        String clean=value.strip();
        if(clean.length()>max||clean.chars().anyMatch(c->c<32&&c!='\n'))throw new AuthProblem(400,message);
        return clean;
    }
}
