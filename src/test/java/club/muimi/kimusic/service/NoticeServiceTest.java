package club.muimi.kimusic.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoticeServiceTest {
    @Test
    void listenerSignalsEachBatchOnce() {
        NoticeService notices = NoticeService.init();
        AtomicInteger notifications = new AtomicInteger();

        notices.addNotice("queued");
        notices.setOnNoticesAvailable(notifications::incrementAndGet);
        notices.addNotice("new");

        assertEquals(1, notifications.get());
        assertEquals("queued", notices.takeNotice());
        assertEquals("new", notices.takeNotice());

        notices.addNotice("next batch");
        assertEquals(2, notifications.get());
    }

    @Test
    void concurrentAddsAndListenerRegistrationProduceOneSignal() throws InterruptedException {
        NoticeService notices = NoticeService.init();
        AtomicInteger notifications = new AtomicInteger();
        var threads = IntStream.range(0, 50)
                .mapToObj(index -> Thread.ofVirtual().unstarted(() -> notices.addNotice("notice " + index)))
                .toList();
        Thread listenerThread = Thread.ofVirtual().unstarted(
                () -> notices.setOnNoticesAvailable(notifications::incrementAndGet));

        listenerThread.start();
        threads.forEach(Thread::start);
        listenerThread.join();
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(1, notifications.get());
    }

    @Test
    void replacingListenerSignalsAlreadyQueuedNotices() {
        NoticeService notices = NoticeService.init();
        AtomicInteger firstListener = new AtomicInteger();
        AtomicInteger replacementListener = new AtomicInteger();

        notices.setOnNoticesAvailable(firstListener::incrementAndGet);
        notices.addNotice("first");
        notices.setOnNoticesAvailable(null);
        notices.addNotice("second");
        notices.setOnNoticesAvailable(replacementListener::incrementAndGet);

        assertEquals(1, firstListener.get());
        assertEquals(1, replacementListener.get());
        assertEquals("first", notices.takeNotice());
        assertEquals("second", notices.takeNotice());
    }

    @Test
    void preservesNoticeLevelAndDefaultsStringNoticesToInfo() {
        NoticeService notices = NoticeService.init();
        notices.addWarning("warning");
        notices.addError("error");
        notices.addNotice("info");

        assertEquals(NoticeLevel.WARNING, notices.takeNoticeEntry().level());
        assertEquals(NoticeLevel.ERROR, notices.takeNoticeEntry().level());
        assertEquals(NoticeLevel.INFO, notices.takeNoticeEntry().level());
    }
}
