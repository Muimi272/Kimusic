package club.muimi.kimusic.service;

import java.util.concurrent.ConcurrentLinkedQueue;

public class NoticeService {

    private final ConcurrentLinkedQueue<String> noticeQueue;

    private NoticeService() {
        noticeQueue = new ConcurrentLinkedQueue<>();
    }

    public static NoticeService init() {
        return new NoticeService();
    }

    public ConcurrentLinkedQueue<String> getNoticeQueue() {
        return noticeQueue;
    }

    public void addNotice(String notice) {
        noticeQueue.add(notice);
    }

    public String takeNotice() {
        return noticeQueue.poll();
    }
}
