#import csv
import os
import math
import pandas as pd
import numpy as np

pd.set_option('display.max_columns', None)
pd.set_option('display.max_rows', None)
pd.set_option('display.max_colwidth', None)

def read_data(input_path):
    # Collect statistic files
    csvfilenames = set()
    for (dirpath, dirnames, filenames) in os.walk(input_path):
        for filename in filenames:
            segments = filename.split(".")
            csvfilenames.add(".".join(segments[0:(len(segments) - 2)]))

    # Read and merge statistic files
    df = pd.DataFrame()
    for filename in csvfilenames:
        print(filename)
        data_tree = pd.read_csv(input_path + filename + ".tree.csv", sep = ';')
        data_other = pd.read_csv(input_path + filename + ".other.csv", sep = ';')
        #df = df.append(data_tree.set_index('Name').join(data_other.set_index('Name')))
        data_tree = data_tree.join(data_other.set_index('Name'), on='Name')

        df = pd.concat([df, data_tree])

    print(df)
    return df

# Columns
#"Name", "Forks", "AllBranches", "RemoteBranches", "LocalBranches",
#"OriginBranches", "Variants", "CommitsAll", "CommitsNoOrphans",
#"CommitsPruned", "Files", "TextFiles", "BinaryFiles", "VarLines",
#"RepoSize", "TreeSize", "FormulaSize", "CommitConditionsSize",
#"AllConditionsSize", "VarTextFilesSize", "VarTextPCFilesSize",
#"VarBinaryFilesSize", "Literals", "ActiveBinFiles", "ActiveTextFiles",
#"ActiveLines"

def remove_row(df, row_name):
    if row_name in df.index:
        df = df.drop(row_name)
    return df

def process_data(df):
    df = df.reset_index()

    # Add newly computed columns
    df['AccessibleForks'] = df['RemoteBranches'].astype(int) - df['OriginBranches'].astype(int)
    df['VariabilityRatio'] = (df['VarLines'].astype(float) - df['ActiveLines'].astype(float)) / df['VarLines'].astype(float)
    df['VariabilityRatio2'] = (df['VarLines'].astype(float) - df['ActiveLines'].astype(float)) / (df['VarLines'].astype(float) * df['CommitsPruned'].astype(float))
    df['ForkRatio'] = df['OriginBranches'].astype(float) / df['AccessibleForks'].astype(float)
    df['ForkBranchRatio'] = df['AccessibleForks'].astype(float) / df['OriginBranches'].astype(float)
    df['VariantBranchRatio'] = df['Variants'].astype(float) / df['AllBranches'].astype(float)
    df['ForkRatio'] = df['OriginBranches'].astype(float) / df['AccessibleForks'].astype(float)
    df['ForkRatio'].replace([np.inf, -np.inf], 0, inplace=True)
    df['VarFilesSize'] = df['VarTextFilesSize'].astype(int) + df['VarBinaryFilesSize'].astype(int) + df['CommitConditionsSize'].astype(int)
    df['VarPCFilesSize'] = df['VarTextPCFilesSize'].astype(int) + df['VarBinaryFilesSize'].astype(int) + df['AllConditionsSize'].astype(int)
    df['VariantRatio'] = df['Variants'].astype(float) / (df['AccessibleForks'].astype(float) + df['OriginBranches'].astype(float))
    #df['ForkIndex'] = df['Variants'].astype(float) / (df['Forks'].astype(float) + df['OriginBranches'].astype(float))
    #df['Metric01'] = (df['Variants'].astype(float) * df['CommitsPruned'].astype(float)) / df['ActiveLines'].astype(float)
    #df['Metric02'] = df['CommitsPruned'].astype(float) / df['ActiveLines'].astype(float)
    #df['Metric03'] = (df['CommitsPruned'].astype(float) - df['Variants'].astype(float)) / df['ActiveLines'].astype(float)

    # Remove unnecessary columns
    df = df.drop(['AllBranches', 'LocalBranches', 'RemoteBranches', 'Literals'], axis=1)
    return df

def round(digits):
    return lambda x: (math.floor(x * pow(10, digits)) / pow(10, digits))

def prefix(unit_prefix):
    return lambda x: (x / pow(10, unit_prefix))

def round_data(df):
    df['VarLines'] = df['VarLines'].map(prefix(3))
    df['ActiveLines'] = df['ActiveLines'].map(prefix(3))
    df['RepoSize'] = df['RepoSize'].map(prefix(6))
    df['TreeSize'] = df['TreeSize'].map(prefix(6))
    df['FormulaSize'] = df['FormulaSize'].map(prefix(6))
    df['VarTextFilesSize'] = df['VarTextFilesSize'].map(prefix(6))
    df['VarTextPCFilesSize'] = df['VarTextPCFilesSize'].map(prefix(6))
    df['VarBinaryFilesSize'] = df['VarBinaryFilesSize'].map(prefix(6))
    df['CommitConditionsSize'] = df['CommitConditionsSize'].map(prefix(6))
    df['AllConditionsSize'] = df['AllConditionsSize'].map(prefix(6))
    df['VarFilesSize'] = df['VarFilesSize'].map(prefix(6))
    df['VarPCFilesSize'] = df['VarPCFilesSize'].map(prefix(6))
    df['VariabilityRatio'] = df['VariabilityRatio'].map(round(3))
    df['VariabilityRatio2'] = df['VariabilityRatio2'].map(prefix(-3)).map(round(3))
    df['ForkRatio'] = df['ForkRatio'].map(round(3))
    df['VariantRatio'] = df['VariantRatio'].map(round(3))
    df['ForkBranchRatio'] = df['ForkBranchRatio'].map(round(3))
    df['VariantBranchRatio'] = df['VariantBranchRatio'].map(round(3))
    return df

def format_data(df):
    df['Name'] = df['Name'].map(lambda x: x.replace('_', '\\_'))
    df['Forks'] = df['Forks'].map('{:,}'.format)
    df['AccessibleForks'] = df['AccessibleForks'].map('{:,}'.format)
    df['OriginBranches'] = df['OriginBranches'].map('{:,}'.format)
    df['Variants'] = df['Variants'].map('{:,}'.format)
    df['CommitsAll'] = df['CommitsAll'].map('{:,}'.format)
    df['CommitsNoOrphans'] = df['CommitsNoOrphans'].map('{:,}'.format)
    df['CommitsPruned'] = df['CommitsPruned'].map('{:,}'.format)
    df['VarLines'] = df['VarLines'].map('{:,.3f}'.format)
    df['ActiveLines'] = df['ActiveLines'].map('{:,.3f}'.format)
    df['RepoSize'] = df['RepoSize'].map('{:,.3f}'.format)
    df['TreeSize'] = df['TreeSize'].map('{:,.3f}'.format)
    df['FormulaSize'] = df['FormulaSize'].map('{:,.3f}'.format)
    df['VarTextFilesSize'] = df['VarTextFilesSize'].map('{:,.3f}'.format)
    df['VarTextPCFilesSize'] = df['VarTextPCFilesSize'].map('{:,.3f}'.format)
    df['VarBinaryFilesSize'] = df['VarBinaryFilesSize'].map('{:,.3f}'.format)
    df['CommitConditionsSize'] = df['CommitConditionsSize'].map('{:,.3f}'.format)
    df['AllConditionsSize'] = df['AllConditionsSize'].map('{:,.3f}'.format)
    df['VarFilesSize'] = df['VarFilesSize'].map('{:,.3f}'.format)
    df['VarPCFilesSize'] = df['VarPCFilesSize'].map('{:,.3f}'.format)
    df['VariabilityRatio'] = df['VariabilityRatio'].map('{:,.3f}'.format)
    df['VariabilityRatio2'] = df['VariabilityRatio2'].map('{:,.3f}'.format)
    df['ForkRatio'] = df['ForkRatio'].map('{:,.3f}'.format)
    df['VariantRatio'] = df['VariantRatio'].map('{:,.3f}'.format)
    df['ForkBranchRatio'] = df['ForkBranchRatio'].map('{:,.3f}'.format)
    df['VariantBranchRatio'] = df['VariantBranchRatio'].map('{:,.3f}'.format)

    # Sort
    #df = df.sort_values(by=['Forks'], ascending=True)
    #df = df.sort_values(by=['VariabilityIndex'], ascending=True)
    df = df.sort_values(by=['Name'], ascending=True, key=lambda col: col.str.lower())
    return df
