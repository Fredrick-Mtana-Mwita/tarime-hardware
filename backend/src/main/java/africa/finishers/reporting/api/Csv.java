package africa.finishers.reporting.api;

import java.util.List;
import java.util.stream.Collectors;

/** Minimal CSV for spreadsheets: quoted cells, and text that could run as a formula in Excel is neutralised. */
final class Csv {
    private Csv(){}
    static String cell(Object value){
        if(value==null)return "";
        String text=value.toString();
        boolean number=value instanceof Number;
        if(!number&&!text.isEmpty()&&"=+-@\t\r".indexOf(text.charAt(0))>=0)text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    static String row(Object... values){return List.of(values).stream().map(Csv::cell).collect(Collectors.joining(","))+"\r\n";}
}
