package com.careflow.region.dto;

import com.careflow.region.entity.Regions;

/**
 * 지역 응답 DTO.
 * <p>
 * JPA 엔티티(Regions)를 컨트롤러에서 그대로 반환하면, 자기참조 parent(LAZY) 프록시
 * (org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor)를 Jackson 이 직렬화하지 못해
 * "Type definition error ... ByteBuddyInterceptor" 500 오류가 발생한다.
 * 따라서 지역 응답은 반드시 이 DTO 로 변환해서 반환한다.
 */
public record RegionResponse(
        Integer regionId,
        String name,
        Integer parentId,
        int depth,
        int sortOrder
) {
    public static RegionResponse from(Regions region) {
        return new RegionResponse(
                region.getId(),
                region.getName(),
                region.getParentId() != null ? region.getParentId().getId() : null,
                region.getDepth(),
                region.getSortOrder()
        );
    }
}
