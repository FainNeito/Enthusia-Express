package io.enthusia.express;

import io.enthusia.express.infrastructure.gui.PostOfficeLayout;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostOfficeLayoutTest {
    @Test void wideCategoryCardsDoNotCreateDeadClickAreas() {
        for(int slot:List.of(0,1,2))assertEquals(1,PostOfficeLayout.INSTANCE.categorySlot(slot));
        for(int slot:List.of(3,4,5))assertEquals(4,PostOfficeLayout.INSTANCE.categorySlot(slot));
        for(int slot:List.of(6,7,8))assertEquals(7,PostOfficeLayout.INSTANCE.categorySlot(slot));
        for(int slot:List.of(-1,9,18,53,54))assertEquals(slot,PostOfficeLayout.INSTANCE.categorySlot(slot));
    }
    @Test void everyPaintedHomeCardRoutesOnlyToItsOwnAction() {
        var labels=List.of("Packages","Letters","Announcements","Sent mail","Mail settings & help",
                "Submit museum artwork","My submissions","Curator queue");
        Map<Integer,Integer> slots=PostOfficeLayout.INSTANCE.home(labels);
        for(int slot:List.of(9,10,11,18,19,20)) assertEquals(0,slots.get(slot));
        for(int slot:List.of(12,13,14,21,22,23)) assertEquals(1,slots.get(slot));
        for(int slot:List.of(15,16,17,24,25,26)) assertEquals(2,slots.get(slot));
        assertEquals(7,slots.get(52));
        assertFalse(slots.containsKey(53),"Close is handled independently of command choices");
        assertTrue(slots.keySet().stream().allMatch(s->s>=0&&s<54));
    }
    @Test void omittedCuratorPermissionDoesNotLeakAStaffClickRegion() {
        var slots=PostOfficeLayout.INSTANCE.home(List.of("Packages","Letters","Announcements","Sent mail",
                "Mail settings & help","Submit museum artwork","My submissions"));
        assertFalse(slots.containsKey(52));
        assertEquals(6,slots.get(45));assertEquals(6,slots.get(51));
    }
    @Test void catalogKeepsAllTwentySevenEntriesSeparateFromFooterControls() {
        var labels=new java.util.ArrayList<>(IntStream.range(0,27).mapToObj(i->"Artwork "+i+" · Awaiting review").toList());
        labels.addAll(List.of("Previous","Next","Filters & search","Express home"));
        var slots=PostOfficeLayout.INSTANCE.catalog(labels);
        for(int i=0;i<27;i++) assertEquals(i,slots.get(i+18));
        assertEquals(27,slots.get(45));assertEquals(28,slots.get(53));
        assertEquals(29,slots.get(51));assertEquals(30,slots.get(52));assertEquals(31,slots.size());
    }
}
