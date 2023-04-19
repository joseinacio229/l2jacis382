package net.sf.l2j.gameserver.model.zone.type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.zone.ZoneType;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.EtcStatusUpdate;

/**
 * A zone extending {@link ZoneType}, which fires a task on the first character entrance.<br>
 * <br>
 * This task launches skill effects on all characters within this zone, and can affect specific class types. It can also be activated or desactivated. The zone is considered a danger zone.
 */
public class EffectZone2 extends ZoneType
{
	private final List<IntIntHolder> _skills = new ArrayList<>(5);
	
	
	private int _chance = 100;
	private int _initialDelay = 0;
	private int _reuseDelay = 30000;
    private int requiredItemId; // ID do item necessário para entrar na zona

	
	private boolean _isEnabled = true;
	
	private Future<?> _task;
	private String _target = "Playable";
	
	public EffectZone2(int id)
	{
		super(id);
	}
	public EffectZone2(int id, int requiredItemId) {
	    super(id);
	    this.requiredItemId = requiredItemId;
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("chance"))
			_chance = Integer.parseInt(value);
		else if (name.equals("initialDelay"))
			_initialDelay = Integer.parseInt(value);
		else if (name.equals("reuseDelay"))
			_reuseDelay = Integer.parseInt(value);
		else if (name.equals("defaultStatus"))
			_isEnabled = Boolean.parseBoolean(value);
		else if (name.equals("skill"))
		{
			final String[] skills = value.split(";");
			for (String skill : skills)
			{
				final String[] skillSplit = skill.split("-");
				if (skillSplit.length != 2)
					LOGGER.warn("Invalid skill format {} for {}.", skill, toString());
				else
				{
					try
					{
						_skills.add(new IntIntHolder(Integer.parseInt(skillSplit[0]), Integer.parseInt(skillSplit[1])));
					}
					catch (NumberFormatException nfe)
					{
						LOGGER.warn("Invalid skill format {} for {}.", skill, toString());
					}
				}
			}
		}
		else if (name.equals("targetType"))
			_target = value;
		else
			super.setParameter(name, value);
	}
	
	@Override
	protected boolean isAffected(Creature character)
	{
		try
		{
			if (!(Class.forName("net.sf.l2j.gameserver.model.actor." + _target).isInstance(character)))
				return false;
		}
		catch (ClassNotFoundException e)
		{
			LOGGER.error("Error for {} on invalid target type {}.", e, toString(), _target);
		}
		return true;
	}
	
	@Override
	protected void onEnter(Creature character)
	{
		if (_task == null)
		{
			synchronized (this)
			{
				if (_task == null)
					_task = ThreadPool.scheduleAtFixedRate(() ->
					{
						if (!_isEnabled)
							return;
						
						if (_characters.isEmpty())
						{
							_task.cancel(true);
							_task = null;
							
							return;
						}
						
						for (Creature temp : _characters.values())
						{
							if (temp.isDead() || Rnd.get(100) >= _chance)
								continue;
							
							for (IntIntHolder entry : _skills)
							{
								final L2Skill skill = entry.getSkill();
								if (skill != null && skill.checkCondition(temp, temp, false) && temp.getFirstEffect(entry.getId()) == null)
									skill.getEffects(temp, temp);
							}
						}
					}, _initialDelay, _reuseDelay);
			}
		}
		
		if (character instanceof Player)
		{
			character.setInsideZone(ZoneId.DANGER_AREA, true);
			character.sendPacket(new EtcStatusUpdate((Player) character));
		}
	}
	
	@Override
	protected void onExit(Creature character)
	{
		if (character instanceof Player)
		{
			character.setInsideZone(ZoneId.DANGER_AREA, false);
			
			if (!character.isInsideZone(ZoneId.DANGER_AREA))
				character.sendPacket(new EtcStatusUpdate((Player) character));
		}
	}
	
	/**
	 * Edit this zone activation state.
	 * @param state : The new state to set.
	 */
	public void editStatus(boolean state)
	{
		_isEnabled = state;
	}
	public boolean canTeleport(Player player) {
		if (requiredItemId > 0 && player.getInventory().getItemByItemId(requiredItemId) == null) {
	        player.sendPacket(SystemMessageId.S1_DOES_NOT_EXIST2);
	        return false;
	    }
	    return true;
	}
}