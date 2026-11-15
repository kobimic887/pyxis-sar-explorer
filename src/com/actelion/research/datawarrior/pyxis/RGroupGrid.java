// Pyxis addition, GPLv3 or later. Pure aggregation: no Swing or table-model dependencies.
package com.actelion.research.datawarrior.pyxis;
import java.util.*;
import java.util.stream.*;
public final class RGroupGrid {
    public static final int MAX_AXIS=100, MAX_CELLS=2500;
    public enum Aggregate { mean, median, min, max, count }
    public record Input(String row, String column, double value, int sourceRow) {}
    public record Cell(double value, List<Integer> sourceRows) { public int count() { return sourceRows.size(); } }
    public final List<String> rows, columns;
    private final Map<List<String>,Cell> cells=new HashMap<>();
    private static String key(String s) { return s==null ? "" : s; }
    public RGroupGrid(List<Input> input, Aggregate aggregate, boolean byMean) {
        Map<List<String>,List<Input>> groups=new HashMap<>();
        for(Input i:input) groups.computeIfAbsent(List.of(key(i.row),key(i.column)),k->new ArrayList<>()).add(i);
        rows=axis(input,true,byMean); columns=axis(input,false,byMean);
        if(rows.size()>MAX_AXIS || columns.size()>MAX_AXIS || (long)rows.size()*columns.size()>MAX_CELLS)
            throw new IllegalArgumentException("Grid exceeds 100 substituents per axis or 2,500 cells. Choose a smaller scaffold family.");
        for(var entry:groups.entrySet()) {
            double[] v=entry.getValue().stream().mapToDouble(Input::value).filter(Double::isFinite).sorted().toArray();
            double value=aggregate==Aggregate.count ? entry.getValue().size() : v.length==0 ? Double.NaN : switch(aggregate) {
                case mean -> Arrays.stream(v).average().orElse(Double.NaN);
                case median -> v[v.length/2]/2+v[(v.length-1)/2]/2;
                case min -> v[0]; case max -> v[v.length-1]; default -> throw new AssertionError();
            };
            cells.put(entry.getKey(),new Cell(value,entry.getValue().stream().map(Input::sourceRow).toList()));
        }
    }
    private static List<String> axis(List<Input> input,boolean row,boolean mean) {
        Map<String,DoubleSummaryStatistics> stats=new TreeMap<>(); Map<String,Integer> counts=new HashMap<>();
        for(Input i:input) { String k=key(row?i.row:i.column); counts.merge(k,1,Integer::sum); var s=stats.computeIfAbsent(k,x->new DoubleSummaryStatistics()); if(Double.isFinite(i.value)) s.accept(i.value); }
        Comparator<String> order=mean ? Comparator.comparingDouble(k->stats.get(k).getCount()==0?Double.POSITIVE_INFINITY:stats.get(k).getAverage()) : Comparator.<String>comparingInt(counts::get).reversed();
        return stats.keySet().stream().sorted(order.thenComparing(Comparator.naturalOrder())).toList();
    }
    public Cell cell(int row,int column) { return cells.get(List.of(rows.get(row),columns.get(column))); }
}
