package com.corporate.agent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DFS Agent Android common request envelope.
 */
public class ApiEnvelope {

    private String channel = "AGNT";
    private String segment = "";
    private String imieNo = "";
    private String latitude = "";
    private String longitude = "";
    private String language = "";
    private JsonNode payload;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public String getImieNo() { return imieNo; }
    public void setImieNo(String imieNo) { this.imieNo = imieNo; }
    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }
    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
