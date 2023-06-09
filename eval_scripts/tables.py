#import csv
import os
import numpy

def out(file, line):
    print(line)
    file.write(line + os.linesep)

def print_table(df, df2, file_path):
    with open(file_path + "_header.tex", 'w') as file:
        #out(file, "\\begin{tabular}{l|rrrr|rrr|rrr|rr}")
        #out(file, "\t\\toprule")
        out(file, "\t\\textbf{System Name} & "
              "\\multicolumn{4}{c|}{\\textbf{Variants}} & "
              "\\multicolumn{3}{c|}{\\textbf{Commits}} & "
              "\\multicolumn{3}{c|}{\\textbf{KLOC}} & "
              "\\multicolumn{2}{c}{\\textbf{FileSize (MB)}} \\\\ ")
        out(file, "\t~ & "
              "\\textit{Forks} & "
              "\\textit{Branches} & "
              "\\textit{Variations} & "
              "\\textit{Ratio} & "
              "\\textit{Total} & "
              "\\textit{NoOrphans} & "
              "\\textit{Pruned} & "
              "\\textit{Total} & "
              "\\textit{$\\varnothing$ Active} & "
              "\\textit{Ratio} & "
         #     "\\textit{Ratio2} & "
              "\\textit{Git} & "
              "\\textit{VariantInc} \\\\ ")
        out(file, "\t\\midrule")

    with open(file_path + "_data.tex", 'w') as file:
        for index, row in df2.iterrows():
            out(file, "" +
                  str(row['Name']) + " & " +
                  str(row['AccessibleForks']) + " & " +
                  str(row['OriginBranches']) + " & " +
                  str(row['Variants']) + " & " +
                  str(row['ForkRatio']) + " & " +
                  str(row['CommitsAll']) + " & " +
                  str(row['CommitsNoOrphans']) + " & " +
                  str(row['CommitsPruned']) + " & " +
                  str(row['VarLines']) + " & " +
                  str(row['ActiveLines']) + " & " +
                  str(row['VariabilityRatio']) + " & " +
                  str(row['RepoSize']) + " & " +
                  str(row['VarFilesSize']) + " \\\\")

        out(file, "\\midrule")
        out(file, "" +
            "Min & " +
            str(df['AccessibleForks'].min()) + " & " +
            str(df['OriginBranches'].min()) + " & " +
            str(df['Variants'].min()) + " & " +
            str(df['ForkRatio'].min()) + " & " +
            str(df['CommitsAll'].min()) + " & " +
            str(df['CommitsNoOrphans'].min()) + " & " +
            str(df['CommitsPruned'].min()) + " & " +
            str(df['VarLines'].min()) + " & " +
            str(df['ActiveLines'].min()) + " & " +
            str(df['VariabilityRatio'].min()) + " & " +
            str(df['RepoSize'].min()) + " & " +
            str(df['VarFilesSize'].min()) + " \\\\")
        out(file, "\t" +
            "Mean & " +
            str(df['AccessibleForks'].mean()) + " & " +
            str(df['OriginBranches'].mean()) + " & " +
            str(df['Variants'].mean()) + " & " +
            str(df['ForkRatio'].mean()) + " & " +
            str(df['CommitsAll'].mean()) + " & " +
            str(df['CommitsNoOrphans'].mean()) + " & " +
            str(df['CommitsPruned'].mean()) + " & " +
            str(df['VarLines'].mean()) + " & " +
            str(df['ActiveLines'].mean()) + " & " +
            str(df['VariabilityRatio'].mean()) + " & " +
            str(df['RepoSize'].mean()) + " & " +
            str(df['VarFilesSize'].mean()) + " \\\\")
        out(file, "\t" +
            "Median & " +
            str(df['AccessibleForks'].median()) + " & " +
            str(df['OriginBranches'].median()) + " & " +
            str(df['Variants'].median()) + " & " +
            str(df['ForkRatio'].median()) + " & " +
            str(df['CommitsAll'].median()) + " & " +
            str(df['CommitsNoOrphans'].median()) + " & " +
            str(df['CommitsPruned'].median()) + " & " +
            str(df['VarLines'].median()) + " & " +
            str(df['ActiveLines'].median()) + " & " +
            str(df['VariabilityRatio'].median()) + " & " +
            str(df['RepoSize'].median()) + " & " +
            str(df['VarFilesSize'].median()) + " \\\\")
        out(file, "\t" +
            "Max & " +
            str(df['AccessibleForks'].max()) + " & " +
            str(df['OriginBranches'].max()) + " & " +
            str(df['Variants'].max()) + " & " +
            str(df['ForkRatio'].max()) + " & " +
            str(df['CommitsAll'].max()) + " & " +
            str(df['CommitsNoOrphans'].max()) + " & " +
            str(df['CommitsPruned'].max()) + " & " +
            str(df['VarLines'].max()) + " & " +
            str(df['ActiveLines'].max()) + " & " +
            str(df['VariabilityRatio'].max()) + " & " +
            str(df['RepoSize'].max()) + " & " +
            str(df['VarFilesSize'].max()) + " \\\\")

        #out(file, "\t\\bottomrule")
        #out(file, "\\end{tabular}")
