package jp.aonir.fuzzyxml;

public interface FuzzyXMLAttribute extends FuzzyXMLNode {
	
	public String getName();
  
  public int getNameOffset();
  
  public int getNameLength();
  
  public int getValueOffset();
  
  public int getValueLength();
	
  public int getValueDataOffset();
  
  public int getValueDataLength();
  
	public void setValue(String value);
	
	public String getValue();
	
  public boolean isQuoted();
  
	public void setQuoteCharacter(char c);
	
	public char getQuoteCharacter();
	
	public void setEscape(boolean escape);
	
	public boolean isEscape();
}