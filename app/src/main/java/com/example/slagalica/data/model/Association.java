package com.example.slagalica.data.model;

import java.util.List;

public class Association {
    private String id;
    private List<String> col1; // 4 reci
    private String col1Solution;
    private List<String> col2;
    private String col2Solution;
    private List<String> col3;
    private String col3Solution;
    private List<String> col4;
    private String col4Solution;
    private String finalSolution;

    public Association() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getCol1() { return col1; }
    public void setCol1(List<String> col1) { this.col1 = col1; }
    public String getCol1Solution() { return col1Solution; }
    public void setCol1Solution(String s) { this.col1Solution = s; }
    public List<String> getCol2() { return col2; }
    public void setCol2(List<String> col2) { this.col2 = col2; }
    public String getCol2Solution() { return col2Solution; }
    public void setCol2Solution(String s) { this.col2Solution = s; }
    public List<String> getCol3() { return col3; }
    public void setCol3(List<String> col3) { this.col3 = col3; }
    public String getCol3Solution() { return col3Solution; }
    public void setCol3Solution(String s) { this.col3Solution = s; }
    public List<String> getCol4() { return col4; }
    public void setCol4(List<String> col4) { this.col4 = col4; }
    public String getCol4Solution() { return col4Solution; }
    public void setCol4Solution(String s) { this.col4Solution = s; }
    public String getFinalSolution() { return finalSolution; }
    public void setFinalSolution(String s) { this.finalSolution = s; }
}