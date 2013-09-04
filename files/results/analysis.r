require(plyr)
require(xtable)

analyze <- function(dir,f){
  
  filename <- paste(dir,"/",f,sep='')
  print(filename)
  #fileName = "rinde.evo4mas.gendreau06.Experiments$RandomBB/experiment_240_24.txt"
  df <- data.frame(read.csv(file=filename,header=TRUE))
  # print(experiment.data)
  #attach(experiment.data)
  
  #print(cost)
  #print(summary(experiment.data$file='req_rapide_1_240_24'))
  
  #df[df$file = 'req_rapide_1_240_24']
  
  #splitted <- split(df,file)
  drops <- c("seed","frequency","duration")
  df <- df[,!(names(df) %in% drops)]
  
  overview <- ddply(df, 'instance',function(x) c(mean=mean(x),sd=sd(x)))
  drops2 <- c("mean.instance","sd.instance")
  overview <- overview[,!(names(overview) %in% drops2)]
  
  means = aggregate(df,list(instance=df$instance),FUN=function(x) cbind(mean(x),sd(x)))
  drops <- c("instance")
  means <- means[,!(names(means) %in% drops)]
  df <- df[,!(names(df) %in% drops)]
  
  
  
  #print(means)
  #print(mean(df))
  #a <- function(x) cbind(mean(x),sd(x))
  
  #print(a(df))
  #means <- rbind(means, a(df))
  
  
  #aggregate(df,FUN=function(x) cbind(mean(x),sd(x)))
  
  #sds = aggregate(df,list(instance=df$instance),FUN=sd)
  #sds <- sds[,!(names(sds) %in% drops)]
  
  #print(means)
  
  #print(means)
  print(overview)
  
  f<- file(paste(dir,"/",f ,".html",sep=''),"w")
  
  print(xtable(overview),f,type="html")
  #write(xtable(overview),f)
  close(f)
  
  
  
  
  #print(splitted)
  # print(splitted$req_rapide_4_240_24)
  
  #
  #for( i in splitted){
    # i contains all data for one file
   
    #print(i$file[1])
    # remove unneeded columns 
    
    #print(i)
    #temp <- i[,!(names(i) %in% drops)]
    
    #a <- list(i$file[1])
    
    #for( n in names(temp)){
      
    #  aggregate()
      
    #  print(n)
    #  a <- c(a,mean(temp[n]))
    #  a <- c(a,sd(temp[n]))
    #} 
   # print(a)
    #print(colMeans(temp))  
  #}
}


setwd(dirname(parent.frame(2)$ofile) )

print(getwd())

dirs <- dir(path=".",pattern="gendreau",recursive=FALSE)

for( dir in dirs){
  # only return *.txt files
  files <- list.files(path=dir,pattern="\\.txt$")
  print(files)
  for( f in files){
    analyze(dir,f)
  }
  
}

