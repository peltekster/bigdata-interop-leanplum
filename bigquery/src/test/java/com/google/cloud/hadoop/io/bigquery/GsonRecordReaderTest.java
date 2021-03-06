package com.google.cloud.hadoop.io.bigquery;

import static org.junit.Assert.assertEquals;

import com.google.cloud.hadoop.fs.gcs.InMemoryGoogleHadoopFileSystem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Unit tests for GsonRecordReader.
 */
@RunWith(JUnit4.class)
public class GsonRecordReaderTest {
  // Sample key values for tests.
  private LongWritable key1 = new LongWritable(0);
  private LongWritable key2 = new LongWritable(35);

  // Sample text values for tests.
  private Text value1 = new Text("{'title':'Test1','value':'test_1'}");
  private Text value2 = new Text("{'title':'Test2','value':'test_2'}");

  // GoogleHadoopGlobalRootedFileSystem to use.
  private FileSystem ghfs;

  // Hadoop job configuration.
  private Configuration config;

  private TaskAttemptID testTaskAttemptId = new TaskAttemptID(
      new TaskID(new JobID("", 1), true /* isMap */, 1), 1);

  /**
   * Create an in-memory GHFS.
   */
  @Before
  public void setUp()
      throws IOException {
    // Set the Hadoop job configuration.
    config = InMemoryGoogleHadoopFileSystem.getSampleConfiguration();

    // Create a GoogleHadoopGlobalRootedFileSystem to use to initialize and write to
    // the in-memory GcsFs.
    ghfs = new InMemoryGoogleHadoopFileSystem();
  }

  /**
   * Iterates through all of the nextKeyValue method of GsonRecordReader.
   */
  @Test
  public void testIterateNextKeyValue()
      throws IOException {
    // Load RecordReader with no records.
    GsonRecordReader recordReader = getRecordReader(0);

    // Assert there are no records to read.
    //TODO(user) Investigate why this flipped in Hadoop 1.2.1
    //assertEquals(recordReader.nextKeyValue(), false);

    // Close RecordReader.
    recordReader.close();

    // Load RecordReader with multiple records. Set length of input split to 60 chars.
    GsonRecordReader multipleRecordReader = getRecordReader(60);

    // Assert there are two records to read.
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(false, multipleRecordReader.nextKeyValue());

    // Close RecordReader.
    multipleRecordReader.close();

    // RecordReader with only part of a file as input. Set length of input split to 30 chars.
    GsonRecordReader smallRecordReader = getRecordReader(30);

    // Assert there is only one record to read.
    assertEquals(true, smallRecordReader.nextKeyValue());
    assertEquals(false, smallRecordReader.nextKeyValue());

    // Close RecordReader.
    multipleRecordReader.close();
  }

  /**
   * Tests getCurrentValue method of GsonRecordReader.
   */
  @Test
  public void testGetCurrentValue()
      throws IOException {
    // Create Json objects from text values.
    JsonParser jsonParser = new JsonParser();
    JsonObject json1 = (JsonObject) jsonParser.parse(value1.toString());
    JsonObject json2 = (JsonObject) jsonParser.parse(value2.toString());

    // Load RecordReader with multiple records. Set length of input split to 60 chars.
    GsonRecordReader multipleRecordReader = getRecordReader(60);

    // Assert RecordReader returns correct Json values.
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(json1, multipleRecordReader.getCurrentValue());
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(json2, multipleRecordReader.getCurrentValue());
    assertEquals(false, multipleRecordReader.nextKeyValue());
    assertEquals(json2, multipleRecordReader.getCurrentValue());

    // Close RecordReader.
    multipleRecordReader.close();
  }

  /**
   * Tests getCurrentKey method of GsonRecordReader.
   */
  @Test
  public void testGetCurrentKey()
      throws IOException {
    // Load RecordReader with multiple records. Set length of input split to 60 chars.
    GsonRecordReader multipleRecordReader = getRecordReader(60);

    // Assert RecordReader returns correct keys.
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(key1, multipleRecordReader.getCurrentKey());
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(key2, multipleRecordReader.getCurrentKey());
    assertEquals(false, multipleRecordReader.nextKeyValue());
    assertEquals(key2, multipleRecordReader.getCurrentKey());

    // Close RecordReader.
    multipleRecordReader.close();
  }

  /**
   * Tests getProgress method of GsonRecordReader.
   */
  @Test
  public void testGetProgress()
      throws IOException {
    // Load RecordReader with multiple records. Set length of input split to 60 chars.
    GsonRecordReader multipleRecordReader = getRecordReader(60);

    // Assert RecordReader returns correct progress.
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(.58, multipleRecordReader.getProgress(), .01);
    assertEquals(true, multipleRecordReader.nextKeyValue());
    assertEquals(1, multipleRecordReader.getProgress(), .01);
    assertEquals(false, multipleRecordReader.nextKeyValue());
    assertEquals(1, multipleRecordReader.getProgress(), .01);

    // Close RecordReader.
    multipleRecordReader.close();
  }

  /**
   * Helper function to get GsonRecordReader with multiple records.
   *
   * @param splitLength the length of the inputSplit in number of chars.
   * @throws IOException on IO Error.
   */
  public GsonRecordReader getRecordReader(int splitLength)
      throws IOException {
    // Create the task context.
    TaskAttemptContext mockJob = Mockito.mock(TaskAttemptContext.class);
    Mockito.when(mockJob.getConfiguration()).thenReturn(config);
    Mockito.when(mockJob.getTaskAttemptID()).thenReturn(testTaskAttemptId);

    // Write values to file.
    ByteBuffer buffer = stringToBytebuffer(value1 + "\n" + value2 + "\n");
    Path mockPath = new Path("gs://test_bucket/test-object");
    writeFile(ghfs, mockPath, buffer);

    // Create a new InputSplit containing the values.
    UnshardedInputSplit inputSplit =
        new UnshardedInputSplit(mockPath, 0, splitLength, new String[0]);

    // Create the GsonRecordReader.
    GsonRecordReader reader = new GsonRecordReader();
    reader.initialize(inputSplit, mockJob);
    return reader;
  }

  /**
   * Helper method to get ByteBuffer from string.
   *
   * @param text the String used to create the ByteBuffer.
   * @return A ByteBuffer of the string.
   * @throws UnsupportedEncodingException when coding scheme unsupported.
   */
  public static ByteBuffer stringToBytebuffer(String text)
      throws UnsupportedEncodingException {
    return ByteBuffer.wrap(text.getBytes("UTF-8"));
  }

  /**
   * Helper method to write buffer to GHFS.
   *
   * @param ghfs the GoogleHadoopGlobalRootedFileSystem to write to.
   * @param hadoopPath the path of the file to write to.
   * @param buffer the buffer to write to the file.
   * @throws IOException on IO Error.
   */
  public static void writeFile(FileSystem ghfs, Path hadoopPath, ByteBuffer buffer)
      throws IOException {
    FSDataOutputStream writeStream = null;
    try {
      writeStream = ghfs.create(hadoopPath, true);
      buffer.clear();
      writeStream.write(buffer.array(), 0, buffer.capacity());
    } finally {
      if (writeStream != null) {
        writeStream.close();
      }
    }
  }
}
