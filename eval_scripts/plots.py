#import csv
#import os
import matplotlib
import matplotlib.pyplot as plt

matplotlib.use('cairo')

def print_plot(file):
    #plt.show()
    plt.savefig(file + '.svg', format="svg")
    plt.savefig(file + '.pdf', format="pdf")

def plot_data(df, output_path):
    df.plot.scatter(x='Variants', y='VariabilityRatio')
    print_plot(output_path + 'variant_variability')

    df.plot.scatter(x='ForkBranchRatio', y='VariantBranchRatio')
    plt.xscale('log')
    print_plot(output_path + 'variantR_forkR')

