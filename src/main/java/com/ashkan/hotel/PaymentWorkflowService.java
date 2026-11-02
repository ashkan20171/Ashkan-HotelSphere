package com.ashkan.hotel;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/** Coordinates booking ownership-independent payment domain actions; callers enforce authorization. */
public final class PaymentWorkflowService {
    private final BookingRepository bookings; private final PaymentRepository payments; private final RefundRepository refunds; private final PaymentGateway gateway;
    public PaymentWorkflowService(BookingRepository bookings,PaymentRepository payments,RefundRepository refunds,PaymentGateway gateway){this.bookings=Objects.requireNonNull(bookings);this.payments=Objects.requireNonNull(payments);this.refunds=Objects.requireNonNull(refunds);this.gateway=Objects.requireNonNull(gateway);}
    public PaymentRepository.Payment authorizeDemo(String bookingId,String idempotencyKey)throws Exception{
        requireKey(idempotencyKey);var booking=bookings.find(bookingId).orElseThrow(()->new IllegalArgumentException("Booking not found"));
        if(!Set.of("PENDING","CONFIRMED").contains(booking.status()))throw new IllegalStateException("Booking is not payable");
        String paymentId="PAY-"+UUID.nameUUIDFromBytes((bookingId+":"+idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Optional<PaymentRepository.Payment> existing=payments.find(paymentId);if(existing.isPresent())return existing.get();
        PaymentRepository.Payment initiated=new PaymentRepository.Payment(paymentId,bookingId,booking.total(),booking.currency(),"DEMO","INITIATED",null);payments.save(initiated);
        PaymentGateway.Authorization result=gateway.authorize(paymentId,booking.total(),booking.currency());
        if(!"AUTHORIZED".equals(result.status()))throw new IllegalStateException("Payment provider did not authorize the transaction");
        PaymentRepository.Payment authorized=new PaymentRepository.Payment(paymentId,bookingId,booking.total(),booking.currency(),"DEMO","AUTHORIZED",result.providerReference());payments.save(authorized);return authorized;
    }
    public RefundRepository.Refund refundDemo(String paymentId,BigDecimal amount,String idempotencyKey)throws Exception{
        requireKey(idempotencyKey);if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("Refund amount must be positive");
        Optional<RefundRepository.Refund> replay=refunds.findByIdempotencyKey(idempotencyKey);if(replay.isPresent()){if(!replay.get().paymentId().equals(paymentId)||replay.get().amount().compareTo(amount)!=0)throw new IllegalArgumentException("Idempotency key was already used for a different refund");return replay.get();}
        Optional<PaymentRepository.Payment> found=payments.find(paymentId);var payment=found.orElseThrow(()->new IllegalArgumentException("Payment not found"));
        if(!Set.of("AUTHORIZED","CAPTURED").contains(payment.status()))throw new IllegalStateException("Payment is not refundable");
        BigDecimal already=refunds.listForPayment(paymentId).stream().filter(r->!"FAILED".equals(r.status())).map(RefundRepository.Refund::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal remaining=payment.amount().subtract(already);if(amount.compareTo(remaining)>0)throw new IllegalArgumentException("Refund exceeds remaining refundable amount");
        String refundId="REF-"+UUID.nameUUIDFromBytes((paymentId+":"+idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var provider=gateway.refund(paymentId,payment.providerReference(),amount,payment.currency());
        return refunds.save(new RefundRepository.Refund(refundId,paymentId,payment.bookingId(),amount,payment.currency(),provider.status(),provider.providerReference(),idempotencyKey));
    }
    static void requireKey(String key){if(key==null||key.isBlank()||key.length()>120)throw new IllegalArgumentException("A valid idempotency key is required");}
}
