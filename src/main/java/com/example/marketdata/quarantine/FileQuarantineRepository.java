package com.example.marketdata.quarantine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Repository
public final class FileQuarantineRepository implements QuarantineRepository {
    private final Path path;
    public FileQuarantineRepository(@Value("${market-data.quarantine-file:./data/quarantine.tsv}") String file){this.path=Path.of(file);}
    @Override public synchronized void save(QuarantinedRecord q){
        var r=q.record();
        try{
            Path parent=path.toAbsolutePath().getParent(); if(parent!=null) Files.createDirectories(parent);
            String row=String.join("\t", esc(q.quarantinedAt()),esc(r.domain().venue()),esc(r.domain().channel()),esc(r.domain().session()),
                    esc(r.sequence()),esc(r.eventTimeNanos()),esc(r.instrument()),esc(r.priceMantissa()),esc(r.quantity()),esc(r.sourceFile()),
                    esc(r.sourceOffset()),esc(q.reason()),esc(q.validator()))+"\n";
            Files.writeString(path,row, StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    private static String esc(Object v){return v==null?"":v.toString().replace("\t"," ").replace("\n"," ");}
}
