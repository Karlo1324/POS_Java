package com.saristore.service;
import com.saristore.config.DatabaseConfig;import java.math.BigDecimal;import java.sql.*;import java.util.Map;
public class POSService {
 public int checkout(Map<Integer,Integer> cart,Integer customerId,boolean credit,BigDecimal tendered)throws SQLException{
  if(cart.isEmpty())throw new SQLException("Cart is empty.");if(credit&&customerId==null)throw new SQLException("Choose a customer for credit sales.");
  try(Connection c=DatabaseConfig.connect()){c.setAutoCommit(false);try{
   BigDecimal total=BigDecimal.ZERO;record Line(int id,int qty,BigDecimal price,BigDecimal cost,int stock){} java.util.List<Line> lines=new java.util.ArrayList<>();
   for(var e:cart.entrySet()){try(PreparedStatement p=c.prepareStatement("SELECT selling_price,cost_price,stock_quantity FROM products WHERE id=? FOR UPDATE")){p.setInt(1,e.getKey());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Product not found");if(e.getValue()<1||r.getInt(3)<e.getValue())throw new SQLException("Insufficient stock for product ID "+e.getKey());BigDecimal price=r.getBigDecimal(1),cost=r.getBigDecimal(2);total=total.add(price.multiply(BigDecimal.valueOf(e.getValue())));lines.add(new Line(e.getKey(),e.getValue(),price,cost,r.getInt(3)));}}}
   if(!credit&&(tendered==null||tendered.compareTo(total)<0))throw new SQLException("Amount tendered is less than total.");
   int saleId;try(PreparedStatement p=c.prepareStatement("INSERT INTO sales(total_amount,payment_type,customer_id,amount_tendered) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setBigDecimal(1,total);p.setString(2,credit?"CREDIT":"CASH");if(customerId==null)p.setNull(3,Types.INTEGER);else p.setInt(3,customerId);p.setBigDecimal(4,credit?BigDecimal.ZERO:tendered);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();saleId=r.getInt(1);}}
   for(Line l:lines){try(PreparedStatement p=c.prepareStatement("UPDATE products SET stock_quantity=stock_quantity-? WHERE id=? AND stock_quantity>=?")){p.setInt(1,l.qty);p.setInt(2,l.id);p.setInt(3,l.qty);if(p.executeUpdate()!=1)throw new SQLException("Stock changed; retry checkout.");}try(PreparedStatement p=c.prepareStatement("INSERT INTO sale_items(sale_id,product_id,quantity,unit_price,unit_cost) VALUES(?,?,?,?,?)")){p.setInt(1,saleId);p.setInt(2,l.id);p.setInt(3,l.qty);p.setBigDecimal(4,l.price);p.setBigDecimal(5,l.cost);p.executeUpdate();}}
   if(credit)try(PreparedStatement p=c.prepareStatement("UPDATE customers SET current_debt=current_debt+? WHERE id=?")){p.setBigDecimal(1,total);p.setInt(2,customerId);if(p.executeUpdate()!=1)throw new SQLException("Customer not found.");}
   c.commit();return saleId;
  }catch(Exception e){c.rollback();if(e instanceof SQLException se)throw se;throw new SQLException(e);}}
 }
}