package dk.medcom.healthcheck.client.videoapi.model;

import java.util.UUID;

public class Meeting {
    private String shortLink;
    private UUID uuid;

    public String getShortLink() {
        return shortLink;
    }

    public void setShortLink(String shortLink) {
        this.shortLink = shortLink;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }
}
