#import csv
import os
from data import *
from tables import *
from plots import *

input_path = "../gen/statistics/"
output_path = "../gen/plots/"

# Create output directory
if not os.path.exists(output_path):
    try:
        os.mkdir(output_path)
    except OSError:
        print ("Failed to create output directory %s" % output_path)
        os.exit(-1)

# Read and merge statistic files
df = read_data(input_path)

# Remove test data
df = remove_row(df, 'varcs-testrepo')
# Remove outlier
#df = remove_row(df, 'v8')

# Remove test data, Add newly computed columns, Remove unnecessary columns
df = process_data(df)

# Save raw data as CSV
df.to_csv(output_path + "statistics_raw.csv", sep=';')

# Round data
df2 = df.copy()
df2 = round_data(df2)

# Plot data
plot_data(df2, output_path)

# Format data
df2 = format_data(df2)

# Print data frame
print(df2.to_string())

# Save processed data as CSV
df2.to_csv(output_path + "statistics_formated.csv", sep=';')

# Print and save latex table
print_table(df, df2, output_path + 'eval_tab')
