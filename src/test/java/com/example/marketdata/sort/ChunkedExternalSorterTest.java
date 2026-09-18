package com.example.marketdata.sort;

import com.example.marketdata.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ChunkedExternalSorterTest {
    private final SequenceDomain d=new SequenceDomain("X","1","S");
    private MarketDataRecord r(long s){return new MarketDataRecord(d,s,0,"ABC",100,1,"f",s);}
    @Test void sortsAcrossRuns() throws Exception {
        var sorter=new ChunkedExternalSorter(2);
        var it=sorter.sort(List.of(r(5),r(1),r(4),r(2),r(3)).iterator());
        List<Long> got=new ArrayList<>(); while(it.hasNext()) got.add(it.next().sequence());
        assertThat(got).containsExactly(1L,2L,3L,4L,5L);
    }
}
