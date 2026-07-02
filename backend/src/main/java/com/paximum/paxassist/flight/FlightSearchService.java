package com.paximum.paxassist.flight;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightSearchService {

    public List<FlightProduct> search(FlightSearchRequest request) {
        // TODO: integrate TourVisio Flight API
        throw new UnsupportedOperationException("Uçuş entegrasyonu henüz tamamlanmadı");
    }
}
