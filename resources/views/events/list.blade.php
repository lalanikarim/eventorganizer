<div>
  {{$events->onEachSide(2)->links()}}

  <table class="table">
    <thead>
    <tr>
      <th>Date</th>
      <th>Name</th>
      <th>Location</th>
      <th></th>
    </tr>
    </thead>
    <tbody>
    @foreach($events as $event)
    <tr>
      <td>{{$event->date}}</td>
      <td>{{$event->name}}</td>
      <td>{{$event->location->name}}</td>
      <td>
        <div class="btn-group" role="group">
          <a href="@routes.Event.get(event.id)" class="btn btn-primary btn-sm">Edit</a>
          <a href="@routes.Event.assignments(event.id)" class="btn btn-secondary btn-sm">Assign</a>
          @if(count($event->children) == 0)
          <a href="@routes.Event.remove(event.id)" class="btn btn-danger btn-sm">Delete</a>
          @endif
        </div>
      </td>
    </tr>
    @endforeach
    </tbody>
  </table>

  {{$events->onEachSide(2)->links()}}
</div>