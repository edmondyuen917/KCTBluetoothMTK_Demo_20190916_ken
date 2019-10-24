package com.kct.bluetooth_demo.db.entity;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;

import java.util.Date;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class GPSInterconn {
    @Id(autoincrement = true)
    private Long id;

    /**
     * 业务ID
     */
    private int transactionId;
    /**
     * 开始运动时间
     */
    @NotNull
    private Date sportStartTime;
    /**
     * 定位时间
     */
    @NotNull
    private Date locationTime;
    private Double latitude;
    private Double longitude;


    @Generated(hash = 1005151791)
    public GPSInterconn(Long id, int transactionId, @NotNull Date sportStartTime,
            @NotNull Date locationTime, Double latitude, Double longitude) {
        this.id = id;
        this.transactionId = transactionId;
        this.sportStartTime = sportStartTime;
        this.locationTime = locationTime;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Generated(hash = 1513898308)
    public GPSInterconn() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public Date getSportStartTime() {
        return this.sportStartTime;
    }

    public void setSportStartTime(Date sportStartTime) {
        this.sportStartTime = sportStartTime;
    }

    public Date getLocationTime() {
        return this.locationTime;
    }

    public void setLocationTime(Date locationTime) {
        this.locationTime = locationTime;
    }

    public Double getLatitude() {
        return this.latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return this.longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

}
