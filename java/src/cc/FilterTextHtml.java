package cc;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.tools.arc.ArcInputFormat;
import org.apache.tika.language.LanguageIdentifier;

public class FilterTextHtml extends Configured implements Tool {

  public static void main(String args[]) throws Exception {
    ToolRunner.run(new FilterTextHtml(), args);
  }
    
  public int run(String[] args) throws Exception {
        
    if (args.length!=2) {
      throw new RuntimeException("usage: "+getClass().getName()+" <input> <output>");
    }
    
    JobConf conf = new JobConf(getConf(), getClass());
    conf.setJobName(getClass().getName());
    
    conf.setOutputKeyClass(Text.class);
    conf.setOutputValueClass(Text.class);
    conf.set("mapred.output.compress", "true");
//    conf.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");
    
    conf.setNumReduceTasks(0);
    
    conf.setInputFormat(ArcInputFormat.class);
    conf.setMapperClass(FilterTextHtmlMapper.class);    
    
    FileInputFormat.addInputPath(conf, new Path(args[0]));
    FileOutputFormat.setOutputPath(conf, new Path(args[1]));
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    
    JobClient.runJob(conf);

    return 0;
  }
  
  private static class FilterTextHtmlMapper extends MapReduceBase implements Mapper<Text,BytesWritable,Text,Text> {
    enum COLUMNS { URL, IP, DTS, MIME_TYPE, SIZE };    
    
    public void map(Text k, BytesWritable v, OutputCollector<Text, Text> collector, Reporter reporter) throws IOException {
   
      try {
        String headerColumns[] = k.toString().split(" ");      
        if (headerColumns.length != COLUMNS.values().length) {
          System.err.println("dodgy header row? ["+k+"]");
          reporter.getCounter("FilterTextHtml", "dodgy_header").increment(1);
          return;
        }
        
        String mime_type = headerColumns[COLUMNS.MIME_TYPE.ordinal()];
        reporter.getCounter("FilterTextHtml.mime_types", mime_type).increment(1);        
        if (!"text/html".equals(mime_type)) {
          return;
        }
          
        // strip header off response        
        String httpResponse = new String(v.getBytes(), 0, v.getLength(), "ISO-8859-1");
        int htmlStartIdx = httpResponse.indexOf("\r\n\r\n"); // ie end of header      
        String html = httpResponse.substring(htmlStartIdx);

        // emit
        String url = headerColumns[COLUMNS.URL.ordinal()];
        String dts = headerColumns[COLUMNS.DTS.ordinal()];
          
        collector.collect(new Text(url+" "+dts), new Text(html));
        
      }      
      catch(Exception e) {        
        reporter.getCounter("FilterTextHtml.exception", e.getClass().getSimpleName()).increment(1);
      }
      
    }   
    
  }
  
}