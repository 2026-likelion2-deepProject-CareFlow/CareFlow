package com.careflow.user.dto;

import com.careflow.user.entity.User;
import lombok.Getter;

@Getter
public class UserAddressResponse {

    private final Integer regionId;
    private final String regionName;
    private final String addressDetail;

    private UserAddressResponse(Integer regionId, String regionName, String addressDetail) {
        this.regionId = regionId;
        this.regionName = regionName;
        this.addressDetail = addressDetail;
    }

    public static UserAddressResponse from(User user) {
        // region이 null이면 regionId·regionName도 null로 응답
        if (user.getRegionId() == null) {
            return new UserAddressResponse(null, null, user.getAddressDetail());
        }
        return new UserAddressResponse(
                user.getRegionId().getId(),
                user.getRegionId().getName(),
                user.getAddressDetail()
        );
    }
}
