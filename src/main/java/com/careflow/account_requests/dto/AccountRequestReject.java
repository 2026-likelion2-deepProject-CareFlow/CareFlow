package com.careflow.account_requests.dto;

import jakarta.validation.constraints.Size;

public record AccountRequestReject (
        @Size(max = 255)
        String rejectReson
){
}
