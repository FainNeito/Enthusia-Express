package io.enthusia.express;

import io.enthusia.express.infrastructure.db.MailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import io.enthusia.express.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class MuseumWorkflowTest {
    @TempDir Path directory;

    private MailRepository open() {
        var repo = new MailRepository(null,directory.resolve("mail.db").toFile(),5000);
        repo.initialize().join(); return repo;
    }

    @Test void artworkRetainsAllTilesCreditAndOwnerScopedHistoryAfterRestart() {
        UUID donor=UUID.randomUUID(); long id;
        try(varScope scope=new varScope(open())) {
            var repo=scope.repo;
            var details=new MuseumDetails("Castle","Original Artist",2,1,"A two-map artwork");
            id=repo.submitArtwork(donor,"Donor",new byte[]{1,2},details,Map.of(42,1,43,1),UUID.randomUUID(),false).join();
            var row=repo.listArtwork(0,new MuseumFilter(donor)).join().getFirst();
            assertEquals(details,row.getDetails());
            assertEquals(Map.of(42,1,43,1),row.getTiles());
            assertEquals("Donor",row.getSubmission().getMail().senderName());
            assertEquals(MuseumState.AWAITING_REVIEW,row.getState());
            assertEquals(0,row.getSubmission().getMail().payload().length);
            assertTrue(repo.listArtwork(0,new MuseumFilter(UUID.randomUUID())).join().isEmpty());
        }
        try(varScope scope=new varScope(open())) {
            assertEquals(2,scope.repo.artwork(id).join().getTiles().size());
            assertArrayEquals(new byte[]{1,2},scope.repo.get(id).join().payload());
        }
    }

    @Test void reviewIsRevisionGuardedAndCannotDisplayBeforeConfirmedDelivery() {
        try(varScope scope=new varScope(open())) {
            var repo=scope.repo; UUID donor=UUID.randomUUID(), manager=UUID.randomUUID();
            long id=repo.submitArtwork(donor,"Donor",new byte[]{1},new MuseumDetails("Landscape","Artist",1,1,""),Map.of(7,1),UUID.randomUUID(),false).join();
            assertFalse(repo.claimMapart(repo.get(id).join(),manager,"Curator").join());
            assertFalse(repo.reviewArtwork(id,0,MuseumState.DISPLAYED,manager,"Curator","").join());
            assertTrue(repo.reviewArtwork(id,0,MuseumState.NEEDS_CHANGES,manager,"Curator","Please correct artist credit").join());
            assertFalse(repo.reviewArtwork(id,0,MuseumState.ACCEPTED,manager,"Curator","").join());
            assertFalse(repo.reviseArtwork(id,1,manager,new MuseumDetails("Landscape","Artist",1,1,"")).join());
            assertTrue(repo.reviseArtwork(id,1,donor,new MuseumDetails("Landscape","Correct Artist",1,1,"")).join());
            assertTrue(repo.reviewArtwork(id,2,MuseumState.ACCEPTED,manager,"Curator","").join());
            assertTrue(repo.claimMapart(repo.get(id).join(),manager,"Curator").join());
            assertFalse(repo.reviewArtwork(id,3,MuseumState.DISPLAYED,manager,"Curator","").join());
            assertTrue(repo.confirmDelivery(id,manager).join());
            var collected=repo.artwork(id).join();
            assertEquals(MuseumState.COLLECTED,collected.getState());
            assertTrue(repo.reviewArtwork(id,collected.getRevision(),MuseumState.DISPLAYED,manager,"Curator","Gallery A").join());
            assertFalse(repo.reviewArtwork(id,collected.getRevision(),MuseumState.DISPLAYED,manager,"Curator","").join());
            var notices=repo.museumNotices(donor).join();
            assertEquals(4,notices.size());
            assertFalse(repo.acknowledgeMuseumNotice(notices.getFirst().getId(),manager).join());
            assertTrue(repo.acknowledgeMuseumNotice(notices.getFirst().getId(),donor).join());
            assertEquals(3,repo.museumNotices(donor).join().size());
            assertTrue(repo.artwork(id).join().getHistory().size()>=6,"Acknowledging notifications cannot erase review history");
        }
    }

    @Test void duplicateMapAdmissionIsAtomicAndTokensCannotCrossDonors() {
        try(varScope scope=new varScope(open())) {
            var repo=scope.repo; UUID donor=UUID.randomUUID(),token=UUID.randomUUID();
            var details=new MuseumDetails("Art","Artist",1,1,"");
            long id=repo.submitArtwork(donor,"Donor",new byte[]{1},details,Map.of(5,1),token,false).join();
            assertEquals(id,repo.submitArtwork(donor,"Donor",new byte[]{1},details,Map.of(5,1),token,false).join());
            assertThrows(Exception.class,()->repo.submitArtwork(UUID.randomUUID(),"Other",new byte[]{1},details,Map.of(5,1),token,false).join());
            assertThrows(Exception.class,()->repo.submitArtwork(donor,"Donor",new byte[]{1},details,Map.of(5,1),UUID.randomUUID(),false).join());
            assertEquals(Set.of(5),repo.duplicateMaps(Set.of(5,6)).join());
            assertNotEquals(id,repo.submitArtwork(donor,"Donor",new byte[]{1},details,Map.of(5,1),UUID.randomUUID(),true).join());
            assertEquals(2,repo.listArtwork(0,new MuseumFilter()).join().size());
        }
    }

    @Test void assignmentAndFiltersAreBoundedAndCannotLeakAnotherDonorHistory() {
        try(varScope scope=new varScope(open())) {
            var repo=scope.repo; UUID donor=UUID.randomUUID(),manager=UUID.randomUUID();
            long id=repo.submitArtwork(donor,"Donor",new byte[]{1},new MuseumDetails("Castle","Painter",1,1,""),Map.of(8,1),UUID.randomUUID(),false).join();
            assertTrue(repo.assignArtwork(id,0,manager,"Curator").join());
            assertFalse(repo.assignArtwork(id,0,UUID.randomUUID(),"Other").join());
            assertEquals(1,repo.listArtwork(0,new MuseumFilter(donor,"cast",MuseumState.AWAITING_REVIEW,manager,false)).join().size());
            assertTrue(repo.listArtwork(0,new MuseumFilter(null,"",null,null,true)).join().isEmpty());
            assertTrue(repo.listArtwork(0,new MuseumFilter(UUID.randomUUID(),"cast",null,null,false)).join().isEmpty());
            assertThrows(IllegalArgumentException.class,()->new MuseumFilter(null,"x".repeat(81),null,null,false));
            assertThrows(IllegalArgumentException.class,()->new MuseumDetails("Title","Artist",0,1,""));
        }
    }

    private static final class varScope implements AutoCloseable {
        private final MailRepository repo;
        varScope(MailRepository repo) { this.repo=repo; }
        public void close() { repo.close(); }
    }

    @Test void existingMuseumMigratesToDurableReviewAndTileHistoryWithoutLosingCargo() throws Exception {
        var file = directory.resolve("mail.db").toFile();
        var repo = new MailRepository(null, file, 5000);
        try {
            repo.initialize().join();
            long id = repo.insertMapart(java.util.UUID.randomUUID(), "Donor", new byte[]{1,2},
                42, "Existing artwork", java.util.UUID.randomUUID()).join();
            try(var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('museum_artworks','museum_tiles','museum_updates')")) {
                int count = 0;
                try(var result = statement.executeQuery()) { while(result.next()) count++; }
                assertEquals(3, count, "Museum needs durable metadata, complete map manifests and recipient updates");
            }
            assertArrayEquals(new byte[]{1,2}, repo.get(id).join().payload());
        } finally { repo.close(); }
    }
}
