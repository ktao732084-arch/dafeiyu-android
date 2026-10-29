package com.xiaoxiao.pet;
import android.app.*;import android.widget.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34) @LooperMode(LooperMode.Mode.PAUSED)
public class GrowthUiTest {
    UiTest fixture=new UiTest();MainActivity activity;PetService service;PetService.Pet pet;
    @Before public void init(){fixture.init();activity=UiTest.ready();service=Robolectric.buildService(PetService.class).get();service.screenWidth=1080;service.screenHeight=1920;service.safeBottom=1920;pet=service.new Pet("main","main");PetService.instance=service;}
    @After public void cleanup()throws Exception{if(activity!=null)activity.finish();GrowthStore.get().flush();PetService.instance=null;fixture.cleanup();}
    @Test public void storyHomeAndListsRenderWithoutChangingTheFrozenPersona()throws Exception{
        String persona=PersonaManager.prompt("main");GrowthPanel.show(activity);String text=UiTest.texts(UiTest.dialog().getWindow().getDecorView());assertTrue(text.contains("泡泡瓶"));assertTrue(text.contains("记忆泡泡"));assertTrue(text.contains("成长记录"));assertEquals(persona,PersonaManager.prompt("main"));
        UiTest.click(UiTest.dialog(),"章节回忆 · 0");assertTrue(UiTest.dialog().isShowing());
    }
    @Test public void freeFoodButtonUsesRealServiceFeedingAndPersists()throws Exception{
        GrowthSheets.feed(activity,pet);assertNotNull(UiTest.find(UiTest.dialog().getWindow().getDecorView(),"白饭"));int before=GrowthStore.get().snapshot().optInt("feeds");UiTest.click(UiTest.dialog(),"白饭");GrowthStore.get().flush();assertEquals(before+1,GrowthStore.get().snapshot().optInt("feeds"));assertFalse(GrowthStore.get().hungry());
    }
    @Test public void lockedFoodsAreShownButDisabled(){GrowthSheets.feed(activity,pet);android.widget.TextView watermelon=UiTest.find(UiTest.dialog().getWindow().getDecorView(),"西瓜 · 第2章解锁");assertNotNull(watermelon);assertFalse(watermelon.isEnabled());}
    @Test public void confirmingPersonaImmediatelyCountsTheFirstMeeting()throws Exception{
        assertTrue(UiTest.texts(activity.content).contains("已见面"));
        int bond=Growth.bond(GrowthStore.get().snapshot());assertEquals(5,bond);assertFalse(GrowthStore.get().snapshot().has("fish"));
        activity.meet();assertEquals(bond,Growth.bond(GrowthStore.get().snapshot()));
    }
    @Test public void moreMenuKeepsTheStoryEntry(){GrowthSheets.more(activity,pet);assertNotNull(UiTest.find(UiTest.dialog().getWindow().getDecorView(),"我们的故事"));assertNotNull(UiTest.find(UiTest.dialog().getWindow().getDecorView(),"隐藏"));}
    @Test public void pendingStoryWaitsForAnAnimationToFinish(){
        boolean active=MainActivity.active;try{MainActivity.active=false;service.screenOn=true;service.keyguard=false;pet.visible=true;pet.frame=new android.widget.FrameLayout(activity);
            // Keep the clock-independent part explicit: an active clip must reject a scene even in daytime.
            pet.busy=true;assertFalse(service.quietMoment(pet));
            pet.busy=false;if(!PetService.moment().night())assertTrue(service.quietMoment(pet));
        }finally{MainActivity.active=active;pet.frame=null;}
    }
}
