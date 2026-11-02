package com.ashkan.hotel;
import java.math.BigDecimal;
public final class PaymentWorkflowContractSmokeTest{
 public static void main(String[]args){PaymentWorkflowService.requireKey("request-1");expect(()->PaymentWorkflowService.requireKey(" "));expect(()->PaymentWorkflowService.requireKey("x".repeat(121)));var g=new DemoPaymentGateway();var a=g.authorize("P1",new BigDecimal("12.00"),"EUR");check(a.simulated()&&"AUTHORIZED".equals(a.status()),"demo authorization is explicitly simulated");var r=g.refund("P1",a.providerReference(),new BigDecimal("5.00"),"EUR");check(r.simulated()&&"SIMULATED".equals(r.status()),"demo refund is explicitly simulated");expect(()->new PaymentRepository.Payment("P2","B2",BigDecimal.ZERO,"EUR","DEMO","INITIATED",null));System.out.println("PaymentWorkflowContractSmokeTest: 6 checks passed");}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static void expect(Runnable r){try{r.run();throw new AssertionError("expected validation failure");}catch(IllegalArgumentException ok){}}
}
