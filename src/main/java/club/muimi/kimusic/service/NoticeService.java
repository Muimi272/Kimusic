package club.muimi.kimusic.service;

import java.util.concurrent.ConcurrentLinkedQueue;

public class NoticeService {

    private final ConcurrentLinkedQueue<Notice> noticeQueue;
    private Runnable noticesAvailableListener;
    private boolean availabilitySignaled;

    private NoticeService() {
        noticeQueue = new ConcurrentLinkedQueue<>();
    }

    public static NoticeService init() {
        return new NoticeService();
    }

    public void addNotice(String notice) {
        addNotice(NoticeLevel.INFO, notice);
    }

    public void addInfo(String notice) {
        addNotice(NoticeLevel.INFO, notice);
    }

    public void addWarning(String notice) {
        addNotice(NoticeLevel.WARNING, notice);
    }

    public void addError(String notice) {
        addNotice(NoticeLevel.ERROR, notice);
    }

    public void addNotice(NoticeLevel level, String message) {
        Runnable listener;
        synchronized (this) {
            noticeQueue.add(new Notice(level, message));
            listener = prepareAvailabilitySignal();
        }
        if (listener != null) {
            listener.run();
        }
    }

    public void setOnNoticesAvailable(Runnable listener) {
        Runnable signal;
        synchronized (this) {
            noticesAvailableListener = listener;
            availabilitySignaled = false;
            signal = prepareAvailabilitySignal();
        }
        if (signal != null) {
            signal.run();
        }
    }

    public synchronized String takeNotice() {
        Notice notice = takeNoticeEntry();
        return notice == null ? null : notice.message();
    }

    public synchronized Notice takeNoticeEntry() {
        Notice notice = noticeQueue.poll();
        if (noticeQueue.isEmpty()) {
            availabilitySignaled = false;
        }
        return notice;
    }

    private Runnable prepareAvailabilitySignal() {
        if (availabilitySignaled || noticesAvailableListener == null || noticeQueue.isEmpty()) {
            return null;
        }
        availabilitySignaled = true;
        return noticesAvailableListener;
    }
}
