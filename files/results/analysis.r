require(plyr)
require(xtable)

analyze <- function(dir,f){
  solName <- unlist(strsplit(f,'_'))[1]
  filename <- paste(dir,"/",f,sep='')
  df <- data.frame(read.csv(file=filename,header=TRUE))
   
  drops <- c("seed","frequency","duration")
  df <- df[,!(names(df) %in% drops)]
    
  overview <- ddply(df, 'instance',function(x) c(mean=mean(x),sd=sd(x)))
    
  drops2 <- c("mean.instance","sd.instance")
  overview <- overview[,!(names(overview) %in% drops2)]
   
  outputFileName <- paste(dir,"/",f ,".html",sep='')
  f<- file(outputFileName,"w")
  print(paste("Written", solName,"to:",outputFileName))
  
  print(xtable(overview),f,type="html")
  close(f)
  
  # only select cost and change name to solution
  cost <- overview["mean.cost"]
  colnames(cost) <- c(solName)

  # add column mean (mean over all files)
  newCost <- rbind(cost,mean(cost))
  return(c(solName,newCost))
}


setwd(dirname(parent.frame(2)$ofile) )

print(paste("Parsing",getwd()))

dirs <- dir(path=".",pattern="gendreau",recursive=FALSE)

for( dir in dirs){
  # only return *.txt files
  files <- list.files(path=dir,pattern="\\.txt$")
  
  dirDf <- data.frame(instance=c(1,2,3,4,5,'mean'),row.names=1)
  #print(files)
  for( f in files){
    res <- analyze(dir,f)
    dirDf[,paste(res[1])] <- res[2]
  }
  #print(dirDf)
  outputFileName <- paste(dir,"/overview.html",sep='')
  f<- file(outputFileName,"w")
  print(xtable(dirDf),f,type="html")
  close(f)
}

