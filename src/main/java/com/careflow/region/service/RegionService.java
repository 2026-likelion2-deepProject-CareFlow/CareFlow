package com.careflow.region.service;

import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    public Regions findByName(String name){
        return regionRepository.findByName(name).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));
    }
}
