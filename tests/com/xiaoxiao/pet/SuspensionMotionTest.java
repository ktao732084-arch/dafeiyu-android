package com.xiaoxiao.pet;
public final class SuspensionMotionTest {
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  SuspensionMotion p=new SuspensionMotion();p.reset(0,0,0);
  for(int i=1;i<=12;i++)p.step(i*16,i*8,0,1);
  check(p.angle>0,"rightward acceleration leaves hanging body behind");
  for(int i=13;i<=240;i++){p.step(i*16,96,0,1);check(Float.isFinite(p.angle)&&Math.abs(p.angle)<=SuspensionMotion.LIMIT,"motion bounded");}
  check(Math.abs(p.angle)<.05f&&Math.abs(p.angularVelocity)<.1f,"still grip settles without perpetual sway");
  p.step(10000,500,800,1);check(Math.abs(p.angle)<.05f,"paused window does not create a huge impulse");
  for(int i=1;i<=300;i++){p.step(10000+i*16,(i%2==0?1000:-1000),i%3*900,2);check(Float.isFinite(p.angle)&&Math.abs(p.angle)<=14,"violent reversals stay finite and bounded");}
  SuspensionMotion a=new SuspensionMotion(),b=new SuspensionMotion();a.reset(0,0,0);b.reset(0,0,0);
  for(int i=1;i<=120;i++)a.step(i*16,(float)(120*Math.sin(i*16/350.0)),0,1);
  for(int i=1;i<=240;i++)b.step(i*8,(float)(120*Math.sin(i*8/350.0)),0,1);
  check(Math.abs(a.angle-b.angle)<2,"similar response at 60 and 120 Hz");
  p.reset(20000,10,20);check(p.angle==0&&p.angularVelocity==0,"new grip clears old momentum");
  System.out.println("PASS: "+checks+" suspension physics checks");
 }
}
