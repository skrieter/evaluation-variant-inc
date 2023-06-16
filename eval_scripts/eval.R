library(ggplot2)
library(plyr)
library(RColorBrewer)
library(extrafont)

#font_import()
loadfonts()

args = commandArgs(trailingOnly=TRUE)

if (length(args)==0) {
  root_path = "../eval"
} else {
  root_path = args[1]
}

in_file = paste(root_path, "plots/statistics_raw.csv", sep = "/")
dataset = read.csv(in_file, TRUE, sep=";")



#--------------------------------------------------------

dataset2 <- subset(dataset, Name!="v8")
ggplot(dataset2, aes(Variants, VariabilityRatio)) +
  geom_point() +
  geom_smooth(se = FALSE) +
  #theme_bw(base_size = 12, base_family="Noto Sans") +
  coord_cartesian(xlim = c(20,650), ylim = c(0,1)) +
  theme(legend.position="bottom",
        legend.title=element_blank(),
        legend.background=element_blank(),
        panel.background=element_blank(),
        panel.border=element_rect(color="black", fill=NA, size=1),
        panel.grid.major=element_blank(),
        panel.grid.minor=element_blank(),
        strip.background = element_blank()) +
  labs(x="Variations", y="KLOC Ratio")

out_file = paste(root_path, "plots/plot_variationsKLOC.pdf", sep = "/")
ggsave(out_file, width = 6,  height = 3)

testResult = capture.output(cor.test(dataset$Variants,
         dataset$VariabilityRatio,
         method = "kendall",
         exact=TRUE,
         continuity = FALSE,
         conf.level = 0.95))
out_file = paste(root_path, "plots/test_significance_VariabilityRatio.txt", sep = "/")
cat(testResult,file=out_file,sep="\n")

#--------------------------------------------------------

ggplot(dataset, aes(AccessibleForks/OriginBranches, VariantBranchRatio)) +
  geom_point() +
  scale_x_log10() +
  geom_smooth(se = FALSE) +
  #theme_bw(base_size = 12, base_family="Noto Sans") +
  theme(legend.position="bottom",
        legend.title=element_blank(),
        legend.background=element_blank(),
        panel.background=element_blank(),
        panel.border=element_rect(color="black", fill=NA, size=1),
        panel.grid.major=element_blank(),
        panel.grid.minor=element_blank(),
        strip.background = element_blank()) +
  labs(x="Ratio of Forks to Branches", y="Ratio of Variants to Forks and Branches")
  
out_file = paste(root_path, "plots/plot_branchForkVariants.pdf", sep = "/")
ggsave(out_file, width = 6,  height = 3.55)

testResult = capture.output(
	cor.test(dataset$AccessibleForks/dataset$OriginBranches,
         dataset$VariantBranchRatio,
         method = "kendall",
         exact=TRUE,
         continuity = FALSE,
         conf.level = 0.95))
out_file = paste(root_path, "plots/test_significance_RatioAccessibleOriginBranches.txt", sep = "/")
cat(testResult,file=out_file,sep="\n",append=T)
         
testResult = capture.output(
	cor.test(dataset$AccessibleForks,
         dataset$VariantBranchRatio,
         method = "kendall",
         exact=TRUE,
         continuity = FALSE,
         conf.level = 0.95))
out_file = paste(root_path, "plots/test_significance_AccessibleForks.txt", sep = "/")
cat(testResult,file=out_file,sep="\n",append=T)

testResult = capture.output(
	cor.test(dataset$OriginBranches,
         dataset$VariantBranchRatio,
         method = "kendall",
         exact=TRUE,
         continuity = FALSE,
         conf.level = 0.95))
out_file = paste(root_path, "plots/test_significance_OriginBranches.txt", sep = "/")
cat(testResult,file=out_file,sep="\n",append=T)
